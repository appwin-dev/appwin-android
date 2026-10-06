package io.appwin.community.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import io.appwin.community.domain.CommunityConfig
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityReactionCount
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.isVideo

/**
 * One post in the feed.
 *
 * Layout mirrors iOS `PostCard` (16 pad, 12 section gaps, 40 avatar, 24 action
 * spacing, stroke glyphs, light tokens) so the two platforms read as one.
 *
 * Type sizes are 2pt under the iOS numbers: Roboto's larger x-height makes the
 * same sp value read bigger than SF Pro, so body 14 / actions 12 match visually.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PostCard(
  post: CommunityPost,
  config: CommunityConfig,
  strings: CommunityStrings,
  onOpen: () -> Unit,
  onReact: (CommunityReactionKind) -> Unit,
  onOpenProfile: (String) -> Unit,
  onEdit: () -> Unit = {},
  onDelete: () -> Unit,
  onReport: () -> Unit,
  onVote: ((String) -> Unit)? = null,
  /** Full-bleed, square corners: the post at the top of its own detail screen. */
  flat: Boolean = false,
) {
  // fontScale is locked in CommunityTheme; card content just renders.
  PostCardContent(
    flat = flat,
    post = post,
    config = config,
    strings = strings,
    onOpen = onOpen,
    onReact = onReact,
    onOpenProfile = onOpenProfile,
    onEdit = onEdit,
    onDelete = onDelete,
    onReport = onReport,
    onVote = onVote,
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostCardContent(
  flat: Boolean,
  post: CommunityPost,
  config: CommunityConfig,
  strings: CommunityStrings,
  onOpen: () -> Unit,
  onReact: (CommunityReactionKind) -> Unit,
  onOpenProfile: (String) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onReport: () -> Unit,
  onVote: ((String) -> Unit)?,
) {
  var expanded by remember(post.id) { mutableStateOf(false) }
  var showsOriginal by remember(post.id) { mutableStateOf(false) }
  var menuOpen by remember(post.id) { mutableStateOf(false) }
  var reactionPickerOpen by remember(post.id) { mutableStateOf(false) }
  var showBreakdown by remember(post.id) { mutableStateOf(false) }
  val reactions = config.features.reactions.ifEmpty { listOf(CommunityReactionKind.LIKE) }
  val defaultKind = CommunityReactionKind.defaultTap(reactions)
  // Long-press tray: configured kinds when several are on, otherwise the full
  // set (same idea as Support's fixed quick reactions).
  val pickerReactions =
    if (reactions.size > 1) reactions else CommunityReactionKind.entries.toList()
  val previewLines = config.limits.feedPreviewLines
  val hasTranslation =
    config.features.translationEnabled && !post.translatedBody.isNullOrBlank()
  val displayedBody =
    if (hasTranslation && !showsOriginal) post.translatedBody.orEmpty() else post.body
  val scale = config.theme.fontScale.scale
  val sizeOf: (Float) -> TextUnit = { (it * scale).sp }

  // Figma feed 5:1837: the pinned badge overlaps the card's top edge, so it
  // lives outside the clipped card and the header makes room for it.
  Box(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(if (flat) androidx.compose.ui.graphics.RectangleShape else MaterialTheme.shapes.medium)
        .background(CommunityColors.surface)
        .clickable(onClick = onOpen),
    ) {
      PostAuthorHeader(
        post = post,
        strings = strings,
        onOpenProfile = onOpenProfile,
        menuOpen = menuOpen,
        onMenuOpenChange = { menuOpen = it },
        config = config,
        onEdit = onEdit,
        onDelete = onDelete,
        onReport = onReport,
        ts = sizeOf,
        modifier = Modifier
          .zIndex(1f)
          .padding(start = 16.dp, end = 16.dp, top = if (post.isPinned) 32.dp else 16.dp),
      )

      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (post.isPendingReview) NoticeBanner(strings.pendingReview)

        if (displayedBody.isNotBlank()) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = displayedBody,
              color = CommunityColors.textPrimary,
              fontSize = sizeOf(16f),
              lineHeight = sizeOf(21f),
              fontWeight = FontWeight.Normal,
              maxLines = if (expanded) Int.MAX_VALUE else previewLines,
              overflow = TextOverflow.Ellipsis,
            )
            val mayOverflow =
              displayedBody.length > previewLines * 44 ||
                displayedBody.lines().size > previewLines
            if ((!expanded && mayOverflow) || expanded || hasTranslation) {
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!expanded && mayOverflow) {
                  InlineAccentButton(strings.seeMore, sizeOf) { expanded = true }
                } else if (expanded) {
                  InlineAccentButton(strings.seeLess, sizeOf) { expanded = false }
                }
                if (hasTranslation) {
                  InlineAccentButton(
                    label = if (showsOriginal) strings.translate else strings.showOriginal,
                    sizeOf = sizeOf,
                    onClick = { showsOriginal = !showsOriginal },
                  )
                }
              }
            }
          }
        }

        post.poll?.let { poll ->
          PostPollBlock(
            poll = poll,
            config = config,
            // Authors see tallies without voting first (`canEdit` = mine).
            showsResults = poll.myOptionId != null || post.canEdit,
            onVote = { onVote?.invoke(it) },
            ts = sizeOf,
          )
        }
      }

      if (post.media.isNotEmpty()) {
        PostMediaGrid(
          media = post.media,
          clipShape = RoundedCornerShape(MediaRadius),
          closeLabel = strings.close,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }

      // Text and poll posts carry a hairline above the counters; a photo
      // already separates them.
      if (post.media.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CommunityColors.surfaceMuted),
        )
      }

      Box {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (config.features.reactionsEnabled) {
              CounterAction(
                label = post.likeCount.toString(),
                contentDescription = strings.like,
                icon = { ReactionGlyph(post.myReaction, size = 20.dp) },
                labelSize = sizeOf(12f),
                onClick = { onReact(post.myReaction ?: defaultKind) },
                onLongClick = {
                  if (post.canEdit && post.likeCount > 0) showBreakdown = true
                  else reactionPickerOpen = true
                },
              )
            }
            if (config.features.commentsEnabled) {
              CounterAction(
                label = post.commentCount.toString(),
                contentDescription = strings.comment,
                icon = {
                  Icon(
                    imageVector = SolarIcons.ChatLine,
                    contentDescription = null,
                    tint = CommunityColors.textTertiary,
                    modifier = Modifier.size(20.dp),
                  )
                },
                labelSize = sizeOf(12f),
                onClick = onOpen,
              )
            }
          }
          if (config.features.viewsEnabled) {
            Text(
              text = strings.viewCountShort(post.viewCount),
              fontSize = sizeOf(12f),
              fontWeight = FontWeight.Medium,
              color = CommunityColors.textTertiary,
            )
          }
        }

        if (reactionPickerOpen) {
          // Popup escapes the card's clip so the pill can sit above the action row.
          Popup(
            alignment = Alignment.TopStart,
            onDismissRequest = { reactionPickerOpen = false },
            offset = with(LocalDensity.current) {
              IntOffset(16.dp.roundToPx(), (-40).dp.roundToPx())
            },
          ) {
            ReactionPickerPill(
              reactions = pickerReactions,
              active = post.myReaction,
              likeCount = if (post.canEdit) post.likeCount else 0,
              breakdownLabel = strings.reactionsTitle(post.likeCount),
              onSelect = { kind ->
                onReact(kind)
                reactionPickerOpen = false
              },
              onShowBreakdown = if (post.canEdit) {
                {
                  reactionPickerOpen = false
                  showBreakdown = true
                }
              } else {
                null
              },
            )
          }
        }
      }
    }

    if (post.isPinned) {
      PinnedBadge(
        label = strings.pinned,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = (-12).dp),
      )
    }
  }

  if (showBreakdown) {
    ReactionBreakdownSheet(
      counts = post.reactionCounts,
      total = post.likeCount,
      strings = strings,
      onDismiss = { showBreakdown = false },
    )
  }
}

/**
 * Figma "Épinglé" (59:7694): a 24dp capsule of the accent at 24 % over
 * bg/container, text/secondary, with a 4dp bg/container stroke drawn outside it
 * that cuts it out of the card edge.
 */
@Composable
private fun PinnedBadge(label: String, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(999.dp))
      .background(CommunityColors.surface)
      .padding(4.dp)
      .height(24.dp)
      .clip(RoundedCornerShape(999.dp))
      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = SolarIcons.Pin,
      contentDescription = null,
      tint = CommunityColors.textSecondary,
      modifier = Modifier.size(12.dp),
    )
    Text(
      text = label,
      fontSize = 10.sp,
      lineHeight = 13.sp,
      fontWeight = FontWeight.Bold,
      color = CommunityColors.textSecondary,
    )
  }
}

/** Figma `rounded-sm`: photos keep a small radius whatever the card's. */
private val MediaRadius = 8.dp

/**
 * Long-press reaction tray - same capsule chrome as Support's
 * `ReactionPickerPill` (shadow + rounded pill + 36dp cells).
 *
 * Optional list cell mirrors iOS: who reacted how lives in the breakdown sheet.
 */
@Composable
internal fun ReactionPickerPill(
  reactions: List<CommunityReactionKind>,
  active: CommunityReactionKind?,
  onSelect: (CommunityReactionKind) -> Unit,
  likeCount: Int = 0,
  breakdownLabel: String? = null,
  onShowBreakdown: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .shadow(12.dp, shape = RoundedCornerShape(999.dp), clip = false)
      .clip(RoundedCornerShape(999.dp))
      .background(CommunityColors.surface)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    reactions.forEach { kind ->
      val selected = kind == active
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (selected) CommunityColors.surfaceMuted else Color.Transparent)
          .clickable { onSelect(kind) },
        contentAlignment = Alignment.Center,
      ) {
        Text(text = kind.emoji, fontSize = 22.sp)
      }
    }
    if (likeCount > 0 && onShowBreakdown != null) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clickable(onClick = onShowBreakdown),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.List,
          contentDescription = breakdownLabel,
          tint = CommunityColors.textTertiary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

/**
 * Heart for empty / love; the picker's emoji for every other kind
 * (parity with iOS `PostCard.likeGlyph`). `LIKE` is 👍, not a heart.
 */
@Composable
internal fun ReactionGlyph(
  myReaction: CommunityReactionKind?,
  size: Dp = 20.dp,
) {
  when (myReaction) {
    null -> Icon(
      imageVector = SolarIcons.Heart,
      contentDescription = null,
      tint = CommunityColors.textTertiary,
      modifier = Modifier.size(size),
    )
    CommunityReactionKind.LOVE -> Icon(
      imageVector = SolarIcons.HeartBold,
      contentDescription = null,
      tint = CommunityColors.like,
      modifier = Modifier.size(size),
    )
    else -> Text(
      text = myReaction.emoji,
      fontSize = (size.value * 0.8f).sp,
      modifier = Modifier.size(size + 4.dp),
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun InlineAccentButton(
  label: String,
  sizeOf: (Float) -> TextUnit,
  onClick: () -> Unit,
) {
  Text(
    text = label,
    fontSize = sizeOf(14f),
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.clickable(onClick = onClick),
  )
}

@Composable
private fun PostMediaGrid(
  media: List<CommunityMedia>,
  clipShape: androidx.compose.ui.graphics.Shape,
  closeLabel: String,
  modifier: Modifier = Modifier,
) {
  when {
    media.isEmpty() -> Unit
    media.size == 1 -> Box(modifier) {
      // Cap portrait height (4:5): taller photos crop top/bottom instead of
      // stretching the feed cell to the full image height.
      SinglePostMedia(
        item = media[0],
        clipShape = clipShape,
        closeLabel = closeLabel,
      )
    }
    // Figma: two photos side by side, tall portrait cells.
    media.size == 2 -> Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      media.forEach { item ->
        MediaCell(
          item = item,
          clipShape = clipShape,
          closeLabel = closeLabel,
          modifier = Modifier.weight(1f).aspectRatio(170f / 300f),
        )
      }
    }
    else -> {
      val cells = media.take(4)
      Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(2).forEach { row ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            row.forEach { item ->
              val absoluteIndex = cells.indexOf(item)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .aspectRatio(1f)
                  .clip(clipShape)
                  .background(CommunityColors.surfaceMuted),
              ) {
                if (item.isVideo) {
                  CommunityVideoCell(
                    url = item.url,
                    contentDescription = item.alt,
                    modifier = Modifier.fillMaxSize(),
                    closeLabel = closeLabel,
                  )
                } else {
                  CommunityTappableImage(
                    url = item.url,
                    contentDescription = item.alt,
                    closeLabel = closeLabel,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                  )
                }
                if (absoluteIndex == 3 && media.size > 4) {
                  Text(
                    text = "+${media.size - 4}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .padding(6.dp)
                      .clip(RoundedCornerShape(50))
                      .background(Color.Black.copy(alpha = 0.55f))
                      .padding(horizontal = 6.dp, vertical = 4.dp),
                  )
                }
              }
            }
            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

/**
 * Single media cell. Mirrors iOS `PostMediaGrid` case 1:
 * - frame aspect = max(natural, 4:5) with natural from metadata, else 4:3
 * - image `scaledToFill` + clip (Compose [ContentScale.Crop] + [Alignment.Center])
 *
 * Do not resize the frame from Coil intrinsic size after load: iOS keeps the
 * metadata/4:3 frame, and swapping aspect mid-load changed the crop vs iOS.
 */
@Composable
private fun SinglePostMedia(
  item: CommunityMedia,
  clipShape: androidx.compose.ui.graphics.Shape,
  closeLabel: String,
) {
  val natural =
    if (item.width != null && item.height != null && item.height > 0) {
      item.width.toFloat() / item.height.toFloat()
    } else {
      4f / 3f
    }
  val ratio = maxOf(natural, MinSingleMediaAspect)
  val frame = Modifier
    .fillMaxWidth()
    .aspectRatio(ratio)
    .clip(clipShape)
    .background(CommunityColors.surfaceMuted)

  if (item.isVideo) {
    CommunityVideoCell(
      url = item.url,
      contentDescription = item.alt,
      modifier = frame,
      contentScale = ContentScale.Crop,
      closeLabel = closeLabel,
    )
    return
  }

  // Same stacking as iOS: fixed aspect frame, then fill+clip inside.
  Box(modifier = frame) {
    CommunityTappableImage(
      url = item.url,
      contentDescription = item.alt,
      closeLabel = closeLabel,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
    )
  }
}

@Composable
private fun MediaCell(
  item: CommunityMedia,
  clipShape: androidx.compose.ui.graphics.Shape,
  closeLabel: String,
  modifier: Modifier,
) {
  Box(modifier = modifier.clip(clipShape).background(CommunityColors.surfaceMuted)) {
    if (item.isVideo) {
      CommunityVideoCell(
        url = item.url,
        contentDescription = item.alt,
        modifier = Modifier.fillMaxSize(),
        closeLabel = closeLabel,
      )
    } else {
      CommunityTappableImage(
        url = item.url,
        contentDescription = item.alt,
        closeLabel = closeLabel,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    }
  }
}

/** Tallest single-media frame in the feed (width / height). Same as iOS. */
private const val MinSingleMediaAspect = 4f / 5f

@Composable
private fun PostAuthorHeader(
  post: CommunityPost,
  strings: CommunityStrings,
  config: CommunityConfig,
  onOpenProfile: (String) -> Unit,
  menuOpen: Boolean,
  onMenuOpenChange: (Boolean) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onReport: () -> Unit,
  ts: (Float) -> TextUnit,
  modifier: Modifier = Modifier,
) {
  val time = relativeTime(post.publishedAtMillis, strings)
  val showReport = config.features.reportingEnabled && !post.canEdit
  val hasMenu = post.canEdit || post.canDelete || showReport
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      CommunityAvatar(
        post.author?.avatarUrl,
        post.author?.nickname.orEmpty(),
        modifier = Modifier.then(
          if (config.features.profilesEnabled && post.author?.isAddressable == true) {
            Modifier.clickable { onOpenProfile(post.author!!.id) }
          } else {
            Modifier
          },
        ),
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = post.author?.nickname.orEmpty(),
            fontSize = ts(14f),
            fontWeight = FontWeight.SemiBold,
            color = CommunityColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (post.hasAdminTag) TeamBadge(strings.teamBadge)
          if (time.isNotEmpty()) {
            Text(
              text = time,
              fontSize = ts(12f),
              fontWeight = FontWeight.Medium,
              color = CommunityColors.textTertiary,
              maxLines = 1,
            )
          }
        }
        if (post.groupName.isNotBlank()) {
          Text(
            text = "#${post.groupName.removePrefix("#")}",
            fontSize = ts(10f),
            fontWeight = FontWeight.Bold,
            color = CommunityColors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    if (hasMenu) {
      Box {
        Box(
          modifier = Modifier
            .size(width = 32.dp, height = 24.dp)
            .clickable { onMenuOpenChange(true) },
          contentAlignment = Alignment.TopEnd,
        ) {
          Icon(
            imageVector = CommunityIcons.Ellipsis,
            contentDescription = null,
            tint = CommunityColors.textTertiary,
            modifier = Modifier.size(16.dp),
          )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
          if (post.canEdit) {
            DropdownMenuItem(
              text = { Text(strings.edit) },
              onClick = { onMenuOpenChange(false); onEdit() },
            )
          }
          if (post.canDelete) {
            DropdownMenuItem(
              text = { Text(strings.delete) },
              onClick = { onMenuOpenChange(false); onDelete() },
            )
          }
          if (showReport) {
            DropdownMenuItem(
              text = { Text(strings.report) },
              onClick = { onMenuOpenChange(false); onReport() },
            )
          }
        }
      }
    }
  }
}

/**
 * Figma feed poll: bg/low options; once results show, a bar fills each one to
 * its share, the member's pick (or the leader) in the accent gradient on a
 * brand-soft track, the others in the border tone, with the percentage.
 */
@Composable
private fun PostPollBlock(
  poll: io.appwin.community.domain.CommunityPoll,
  config: CommunityConfig,
  showsResults: Boolean,
  onVote: (String) -> Unit,
  ts: (Float) -> TextUnit,
) {
  val accent = MaterialTheme.colorScheme.primary
  val optionShape = RoundedCornerShape(config.radiusField)
  val hasVoted = poll.myOptionId != null
  val leaderVotes = poll.options.maxOfOrNull { it.voteCount } ?: 0

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    poll.options.forEach { option ->
      val ratio =
        if (poll.totalVotes > 0) option.voteCount.toFloat() / poll.totalVotes.toFloat() else 0f
      val highlighted = showsResults && (
        poll.myOptionId == option.id ||
          (!hasVoted && leaderVotes > 0 && option.voteCount == leaderVotes)
        )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(optionShape)
          .background(if (highlighted) accent.copy(alpha = 0.24f) else CommunityColors.surfaceMuted)
          .then(if (!hasVoted) Modifier.clickable { onVote(option.id) } else Modifier),
      ) {
        if (showsResults && ratio > 0f) {
          Box(modifier = Modifier.matchParentSize()) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .clip(optionShape)
                .then(
                  if (highlighted) Modifier.background(accentPillBrush(config))
                  else Modifier.background(CommunityColors.border),
                ),
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = option.text,
            fontSize = ts(14f),
            fontWeight = FontWeight.Medium,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary else CommunityColors.textPrimary,
            modifier = Modifier.weight(1f),
          )
          if (showsResults) {
            Text(
              text = "${(ratio * 100).roundToInt()}%",
              fontSize = ts(12f),
              fontWeight = FontWeight.Medium,
              color = CommunityColors.textPrimary,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CounterAction(
  label: String,
  contentDescription: String,
  icon: @Composable () -> Unit,
  labelSize: TextUnit,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier
      .widthIn(min = 64.dp)
      .semantics { this.contentDescription = contentDescription }
      .then(
        if (onLongClick != null) {
          Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        } else {
          Modifier.clickable(onClick = onClick)
        },
      ),
  ) {
    icon()
    Text(
      text = label,
      fontSize = labelSize,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textTertiary,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReactionBreakdownSheet(
  counts: List<CommunityReactionCount>,
  total: Int,
  strings: CommunityStrings,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Text(
      text = strings.reactionsTitle(total),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    counts.forEach { entry ->
      ListItem(
        headlineContent = { Text(strings.reactionLabel(entry.kind)) },
        leadingContent = {
          Text(text = entry.kind.emoji, fontSize = 22.sp)
        },
        trailingContent = {
          Text(
            text = entry.count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = CommunityColors.textSecondary,
          )
        },
      )
    }
    TextButton(
      onClick = onDismiss,
      modifier = Modifier.fillMaxWidth().padding(24.dp),
    ) { Text(strings.close) }
  }
}
