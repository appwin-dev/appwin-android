package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.isVideo

/**
 * One comment - Figma commentary (19:5233, 178:2456): a 24dp avatar and a
 * bg/low bubble holding the author line, the body, then « Répondre » and the
 * reaction summary. A long press opens the emoji bar, « ⋯ » the actions.
 */
@Composable
internal fun CommentRow(
  comment: CommunityComment,
  strings: CommunityStrings,
  canReact: Boolean,
  canReply: Boolean,
  availableReactions: List<CommunityReactionKind>,
  onOpenProfile: (String) -> Unit,
  onReact: (CommunityComment, CommunityReactionKind) -> Unit,
  onReply: (CommunityComment) -> Unit,
  indent: Int,
  canOpenProfile: Boolean = true,
  /** The « ⋯ » ([ActionsMenu]); none where the comment is shown for a decision. */
  actionsMenu: (@Composable (CommunityComment) -> Unit)? = null,
) {
  val body = comment.translatedBody ?: comment.body
  val reactions = availableReactions.ifEmpty { listOf(CommunityReactionKind.LOVE) }
  val defaultKind = CommunityReactionKind.defaultTap(reactions)
  val offersEmojis = reactions.size > 1
  var barOpen by remember(comment.id) { mutableStateOf(false) }
  var showBreakdown by remember(comment.id) { mutableStateOf(false) }
  val openProfile = if (canOpenProfile) {
    comment.author?.takeIf { it.isAddressable }?.id?.let { id -> { onOpenProfile(id) } }
  } else {
    null
  }
  val radius = LocalCommunityConfig.current.radiusField

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = (indent * 32).dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    AuthorAvatar(
      author = comment.author,
      size = 24,
      modifier = Modifier
        .reactionDimmed(12.dp)
        .then(if (openProfile != null) Modifier.clickable(onClick = openProfile) else Modifier),
    )
    Box(modifier = Modifier.weight(1f)) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .reactionFocusable("comment:${comment.id}", barOpen, { barOpen = it }, radius)
          .clip(RoundedCornerShape(radius))
          .background(if (barOpen) CommunityColors.surface else CommunityColors.surfaceMuted)
          .communityPress(
            onTap = { barOpen = false },
            onLongPress = {
              if (!canReact) return@communityPress
              if (offersEmojis) barOpen = true
              else if (comment.canEdit && comment.likeCount > 0) showBreakdown = true
            },
          )
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier
                .weight(1f)
                .then(if (openProfile != null) Modifier.clickable(onClick = openProfile) else Modifier),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = comment.author?.nickname.orEmpty(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = CommunityColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              if (comment.author?.isTeam == true) TeamBadge(strings.teamBadge, onMutedSurface = true)
              Text(
                text = relativeTime(comment.createdAtMillis, strings),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = CommunityColors.textTertiary,
                maxLines = 1,
              )
            }
            actionsMenu?.invoke(comment)
          }
          if (body.isNotBlank()) {
            Text(text = body, fontSize = 14.sp, lineHeight = 18.sp, color = CommunityColors.textPrimary)
          }
        }

        if (comment.media.isNotEmpty()) CommentMedia(comment, strings)

        Row(verticalAlignment = Alignment.CenterVertically) {
          // Reply is allowed on replies too: the API re-parents under the root.
          if (canReply) {
            Text(
              text = strings.reply,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = CommunityColors.textTertiary,
              modifier = Modifier.clickable { onReply(comment) },
            )
          }
          Spacer(Modifier.weight(1f))
          if (canReact) {
            // Figma: the three most used emojis and the count; a lone heart until someone reacts.
            Box(modifier = Modifier.clickable { onReact(comment, comment.myReaction ?: defaultKind) }) {
              if (comment.likeCount > 0) {
                val top = comment.topReactions.ifEmpty { listOf(comment.myReaction ?: defaultKind) }
                ReactionSummary(top, comment.likeCount)
              } else {
                HeartGlyph(filled = false, size = 14.dp)
              }
            }
          }
        }
      }

      if (barOpen) {
        Popup(
          alignment = Alignment.TopStart,
          onDismissRequest = { barOpen = false },
          offset = with(LocalDensity.current) { IntOffset((-24).dp.roundToPx(), (-60).dp.roundToPx()) },
        ) {
          ReactionBar(reactions, comment.myReaction, strings, onPick = { kind ->
            onReact(comment, kind)
            barOpen = false
          })
        }
      }
    }
  }

  if (showBreakdown) {
    ReactionBreakdownSheet(
      counts = comment.reactionCounts,
      total = comment.likeCount,
      strings = strings,
      onDismiss = { showBreakdown = false },
    )
  }
}

@Composable
private fun CommentMedia(comment: CommunityComment, strings: CommunityStrings) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    items(comment.media, key = { it.url }) { item ->
      val cell = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))
      if (item.isVideo) {
        CommunityVideoCell(url = item.url, contentDescription = item.alt, modifier = cell, closeLabel = strings.close)
      } else {
        CommunityTappableImage(
          url = item.url,
          contentDescription = item.alt,
          closeLabel = strings.close,
          modifier = cell,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}
