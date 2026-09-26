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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
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
) {
  // fontScale is locked in CommunityTheme; card content just renders.
  PostCardContent(
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
  val reactions = config.features.reactions.ifEmpty { listOf(CommunityReactionKind.LIKE) }
  val defaultKind = reactions.first()
  // Long-press tray: configured kinds when several are on, otherwise the full
  // set (same idea as Support's fixed quick reactions).
  val pickerReactions =
    if (reactions.size > 1) reactions else CommunityReactionKind.entries.toList()
  val previewLines = config.limits.feedPreviewLines
  val hasTranslation =
    config.features.translationEnabled && !post.translatedBody.isNullOrBlank()
  val displayedBody =
    if (hasTranslation && !showsOriginal) post.translatedBody.orEmpty() else post.body
  val cardRadius = MaterialTheme.shapes.medium
  val imageRadius = MaterialTheme.shapes.small
  val scale = config.theme.fontScale.scale
  val sizeOf: (Float) -> TextUnit = { (it * scale).sp }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(cardRadius)
      .background(CommunityColors.surface)
      .clickable(onClick = onOpen)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
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
      modifier = Modifier.zIndex(1f),
    )

    if (post.isPendingReview) NoticeBanner(strings.pendingReview)

    if (displayedBody.isNotBlank()) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = displayedBody,
          color = CommunityColors.textPrimary,
          fontSize = sizeOf(14f),
          lineHeight = sizeOf(19f),
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

    if (post.media.isNotEmpty()) {
      PostMediaGrid(media = post.media, clipShape = imageRadius, closeLabel = strings.close)
    }

    post.poll?.let { poll ->
      PostPollBlock(
        poll = poll,
        strings = strings,
        // Authors see tallies without voting first (`canEdit` = mine).
        showsResults = poll.myOptionId != null || post.canEdit,
        onVote = { onVote?.invoke(it) },
        ts = sizeOf,
      )
    }

    if (
      config.features.reactionsEnabled ||
      post.commentCount > 0 ||
      (config.features.viewsEnabled && post.viewCount > 0)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (config.features.reactionsEnabled) {
          if (post.likeCount > 0) {
            var showBreakdown by remember(post.id) { mutableStateOf(false) }
            val kinds = post.topReactions.ifEmpty {
              post.reactionCounts
                .sortedByDescending { it.count }
                .map { it.kind }
                .ifEmpty { listOfNotNull(post.myReaction) }
            }.take(3)
            val emoji = kinds.joinToString("") { it.emoji }.ifEmpty { "❤️" }
            Text(
              text = "$emoji ${post.likeCount}",
              fontSize = sizeOf(11f),
              color = CommunityColors.textSecondary,
              modifier = Modifier.clickable { showBreakdown = true },
            )
            if (showBreakdown) {
              ReactionBreakdownSheet(
                counts = post.reactionCounts,
                total = post.likeCount,
                strings = strings,
                onDismiss = { showBreakdown = false },
              )
            }
          } else {
            Text(
              text = strings.beFirstToReact,
              fontSize = sizeOf(11f),
              color = CommunityColors.textTertiary,
            )
          }
        } else {
          Box {}
        }

        val parts = buildList {
          add(strings.commentCountShort(post.commentCount))
          if (config.features.viewsEnabled) {
            add(strings.viewCountShort(post.viewCount))
          }
        }
        Text(
          text = parts.joinToString(" · "),
          fontSize = sizeOf(11f),
          color = CommunityColors.textTertiary,
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(CommunityColors.border),
    )

    Box {
      Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (config.features.reactionsEnabled) {
          val reacted = post.myReaction != null
          val tint =
            if (reacted) MaterialTheme.colorScheme.primary else CommunityColors.textTertiary
          CounterAction(
            label = strings.like,
            icon = {
              Icon(
                imageVector = if (reacted) CommunityIcons.HeartFilled else CommunityIcons.Heart,
                contentDescription = strings.like,
                tint = tint,
                modifier = Modifier.size(16.dp),
              )
            },
            tint = tint,
            labelSize = sizeOf(12f),
            onClick = { onReact(defaultKind) },
            onLongClick = { reactionPickerOpen = true },
          )
        }

        if (config.features.commentsEnabled) {
          CounterAction(
            label = strings.comment,
            icon = {
              Icon(
                imageVector = CommunityIcons.Bubble,
                contentDescription = strings.comment,
                tint = CommunityColors.textTertiary,
                modifier = Modifier.size(16.dp),
              )
            },
            tint = CommunityColors.textTertiary,
            labelSize = sizeOf(12f),
            onClick = onOpen,
          )
        }
      }

      if (reactionPickerOpen) {
        // Popup escapes the card's clip so the pill can sit above the action row.
        Popup(
          alignment = Alignment.TopStart,
          onDismissRequest = { reactionPickerOpen = false },
          offset = with(LocalDensity.current) {
            IntOffset(0, (-52).dp.roundToPx())
          },
        ) {
          ReactionPickerPill(
            reactions = pickerReactions,
            active = post.myReaction,
            onSelect = { kind ->
              onReact(kind)
              reactionPickerOpen = false
            },
          )
        }
      }
    }
  }
}

/**
 * Long-press reaction tray - same capsule chrome as Support's
 * `ReactionPickerPill` (shadow + rounded pill + 36dp cells).
 */
@Composable
internal fun ReactionPickerPill(
  reactions: List<CommunityReactionKind>,
  active: CommunityReactionKind?,
  onSelect: (CommunityReactionKind) -> Unit,
) {
  Row(
    modifier = Modifier
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
    fontSize = sizeOf(11f),
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
) {
  when {
    media.isEmpty() -> Unit
    media.size == 1 -> {
      // Cap portrait height (4:5): taller photos crop top/bottom instead of
      // stretching the feed cell to the full image height.
      SinglePostMedia(
        item = media[0],
        clipShape = clipShape,
        closeLabel = closeLabel,
      )
    }
    else -> {
      val cells = media.take(4)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                  .background(CommunityColors.border.copy(alpha = 0.4f)),
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
    .background(CommunityColors.border.copy(alpha = 0.4f))

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
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier.fillMaxWidth(),
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
          fontSize = ts(13f),
          fontWeight = FontWeight.SemiBold,
          color = CommunityColors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (post.hasAdminTag) {
          TeamBadge(strings.teamBadge)
        }
        if (time.isNotEmpty()) {
          Text(
            text = time,
            fontSize = ts(10f),
            color = CommunityColors.textTertiary,
            maxLines = 1,
          )
        }
        if (post.isPinned) {
          Icon(
            imageVector = CommunityIcons.Pin,
            contentDescription = null,
            tint = CommunityColors.textTertiary,
            modifier = Modifier.size(10.dp),
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (hasMenu) {
          Box {
            // Hit target stays on the nickname row (not vertically centered on
            // the whole author block with the group chip under it).
            Box(
              modifier = Modifier
                .size(width = 44.dp, height = 32.dp)
                .clickable { onMenuOpenChange(true) },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = CommunityIcons.Ellipsis,
                contentDescription = null,
                tint = CommunityColors.textTertiary,
                modifier = Modifier.size(17.dp),
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
      if (post.groupName.isNotBlank()) {
        Text(
          text = post.groupName,
          fontSize = ts(10f),
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun PostPollBlock(
  poll: io.appwin.community.domain.CommunityPoll,
  strings: CommunityStrings,
  showsResults: Boolean,
  onVote: (String) -> Unit,
  ts: (Float) -> TextUnit,
) {
  // Same tokens as iOS `PostPollView`: border@0.35 track, accent@0.35/0.15 fill.
  val accent = MaterialTheme.colorScheme.primary
  val optionRadius = MaterialTheme.shapes.small
  val hasVoted = poll.myOptionId != null

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    poll.options.forEach { option ->
      val selected = poll.myOptionId == option.id
      val ratio =
        if (poll.totalVotes > 0) option.voteCount.toFloat() / poll.totalVotes.toFloat() else 0f
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 40.dp)
          .clip(optionRadius)
          .background(CommunityColors.border.copy(alpha = 0.35f))
          .then(
            if (!hasVoted) Modifier.clickable { onVote(option.id) } else Modifier,
          ),
      ) {
        if (showsResults && ratio > 0f) {
          // Width-only fill (not matchParentSize): otherwise the accent paints
          // the whole row and the poll no longer matches iOS GeometryReader bars.
          Box(
            modifier = Modifier
              .matchParentSize()
              .align(Alignment.CenterStart),
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .background(accent.copy(alpha = if (selected) 0.35f else 0.15f)),
            )
          }
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = option.text,
            fontSize = ts(14f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = CommunityColors.textPrimary,
            modifier = Modifier.weight(1f),
          )
          if (showsResults) {
            Text(
              text = option.voteCount.toString(),
              fontSize = ts(13f),
              color = CommunityColors.textSecondary,
            )
          }
        }
      }
    }
    if (poll.totalVotes > 0) {
      Text(
        text = strings.pollVotes(poll.totalVotes),
        fontSize = ts(12f),
        color = CommunityColors.textTertiary,
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CounterAction(
  label: String,
  icon: @Composable () -> Unit,
  tint: Color,
  labelSize: TextUnit,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
      .heightIn(min = 32.dp)
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
      color = tint,
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
