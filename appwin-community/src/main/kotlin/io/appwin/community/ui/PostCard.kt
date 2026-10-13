package io.appwin.community.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import io.appwin.community.domain.CommunityConfig
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityReactionKind

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
  onVote: ((String) -> Unit)? = null,
  /** Full-bleed, square corners: the post at the top of its own detail screen. */
  flat: Boolean = false,
  /** The « ⋯ » over the card's corner ([ActionsMenu]); none where the post is shown for a decision. */
  actionsMenu: (@Composable (Modifier) -> Unit)? = null,
  /** Moderation queue: the sanction in place of the « ⋯ » (Figma 182:857). */
  sanction: SanctionKind? = null,
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
    onVote = onVote,
    actionsMenu = actionsMenu,
    sanction = sanction,
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
  onVote: ((String) -> Unit)?,
  actionsMenu: (@Composable (Modifier) -> Unit)?,
  sanction: SanctionKind?,
) {
  var expanded by remember(post.id) { mutableStateOf(false) }
  var showsOriginal by remember(post.id) { mutableStateOf(false) }
  var reactionPickerOpen by remember(post.id) { mutableStateOf(false) }
  var showBreakdown by remember(post.id) { mutableStateOf(false) }
  val reactions = config.features.reactions.ifEmpty { listOf(CommunityReactionKind.LOVE) }
  val defaultKind = CommunityReactionKind.defaultTap(reactions)
  val offersEmojis = config.features.offersEmojiReactions
  // Same radius as the card's clip (`MaterialTheme.shapes.medium`), so the dim covers it exactly.
  val cardRadius = if (flat) 0.dp else config.radiusField
  val previewLines = config.limits.feedPreviewLines
  val hasTranslation =
    config.features.translationEnabled && !post.translatedBody.isNullOrBlank()
  val displayedBody =
    if (hasTranslation && !showsOriginal) post.translatedBody.orEmpty() else post.body
  val scale = config.theme.fontScale.scale
  val sizeOf: (Float) -> TextUnit = { (it * scale).sp }

  // Figma feed 5:1837: the pinned badge overlaps the card's top edge, so it
  // lives outside the clipped card and the header makes room for it.
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .reactionFocusable("post:${post.id}", reactionPickerOpen, { reactionPickerOpen = it }, cardRadius),
  ) {
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
        config = config,
        sanction = sanction,
        reservesMenu = actionsMenu != null,
        ts = sizeOf,
        modifier = Modifier
          .zIndex(1f)
          .padding(start = 16.dp, end = 16.dp, top = if (post.isPinned) 32.dp else 16.dp),
      )

      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
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
                // Always the heart: the emojis themselves live in the summary on the right.
                icon = { HeartGlyph(filled = post.myReaction != null, size = 20.dp) },
                labelSize = sizeOf(12f),
                onClick = { onReact(post.myReaction ?: defaultKind) },
                onLongClick = {
                  if (offersEmojis) reactionPickerOpen = true
                  else if (post.canEdit && post.likeCount > 0) showBreakdown = true
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
          // Figma 178:2023: views, a rule, the three most used emojis, 10 apart.
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (config.features.viewsEnabled) {
              Text(
                text = strings.viewCountShort(post.viewCount),
                fontSize = sizeOf(12f),
                fontWeight = FontWeight.Medium,
                color = CommunityColors.textTertiary,
              )
            }
            if (config.features.reactionsEnabled && offersEmojis && post.topReactions.isNotEmpty()) {
              if (config.features.viewsEnabled) {
                Box(Modifier.size(width = 1.dp, height = 16.dp).background(CommunityColors.border))
              }
              Box(
                modifier = Modifier.clickable(enabled = post.canEdit) { showBreakdown = true },
              ) { ReactionSummary(post.topReactions) }
            }
          }
        }

        if (reactionPickerOpen) {
          // Popup escapes the card's clip so the bar can sit above the action row.
          Popup(
            alignment = Alignment.TopStart,
            onDismissRequest = { reactionPickerOpen = false },
            offset = with(LocalDensity.current) {
              IntOffset(12.dp.roundToPx(), (-52).dp.roundToPx())
            },
          ) {
            ReactionBar(reactions, post.myReaction, strings, onPick = { kind ->
              onReact(kind)
              reactionPickerOpen = false
            })
          }
        }
      }
    }

    if (actionsMenu != null && sanction == null) {
      // Over the card's corner, its padding included.
      actionsMenu(
        Modifier
          .align(Alignment.TopEnd)
          .zIndex(2f)
          .size(width = 80.dp, height = if (post.isPinned) 76.dp else 60.dp),
      )
    }

    if (post.isPinned) {
      PinnedBadge(
        label = strings.pinned,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .offset(y = (-12).dp)
          .reactionDimmed(999.dp, ownerId = "post:${post.id}"),
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
private fun PostAuthorHeader(
  post: CommunityPost,
  strings: CommunityStrings,
  config: CommunityConfig,
  onOpenProfile: (String) -> Unit,
  sanction: SanctionKind?,
  /** Room for the « ⋯ » drawn over the corner by the card. */
  reservesMenu: Boolean,
  ts: (Float) -> TextUnit,
  modifier: Modifier = Modifier,
) {
  val time = relativeTime(post.publishedAtMillis, strings)
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
      AuthorAvatar(
        author = post.author,
        size = 40,
        modifier = Modifier.then(
          if (config.features.profilesEnabled && post.author?.isAddressable == true) {
            Modifier.clickable { onOpenProfile(post.author.id) }
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

    when {
      sanction != null -> SanctionBadge(sanction, strings)
      reservesMenu -> Spacer(Modifier.size(24.dp))
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
          Modifier.communityPress(onTap = onClick, onLongPress = onLongClick)
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

