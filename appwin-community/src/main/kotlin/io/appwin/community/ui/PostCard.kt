package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.appwin.community.domain.CommunityConfig
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityReactionKind

/**
 * One post in the feed.
 *
 * The body is truncated to `feedPreviewLines` with a "see more": a long post
 * must not monopolise the screen of a feed being scrolled.
 */
@Composable
internal fun PostCard(
  post: CommunityPost,
  config: CommunityConfig,
  strings: CommunityStrings,
  onOpen: () -> Unit,
  onReact: (CommunityReactionKind) -> Unit,
  onOpenProfile: (String) -> Unit,
  onDelete: () -> Unit,
  onReport: () -> Unit,
) {
  var expanded by remember(post.id) { mutableStateOf(false) }
  var menuOpen by remember(post.id) { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .clip(MaterialTheme.shapes.medium)
      .background(MaterialTheme.colorScheme.surface)
      .clickable(onClick = onOpen)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    AuthorRow(
      author = post.author,
      subtitle = buildString {
        append(post.groupName)
        val time = relativeTime(post.publishedAtMillis)
        if (time.isNotEmpty()) append(" · ").append(time)
        if (post.editedAtMillis != null) append(" · ").append(strings.edited)
      },
      strings = strings,
      onClick = post.author?.id?.let { id -> { onOpenProfile(id) } },
      trailing = {
        Box {
          IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
            Icon(
              Icons.Default.MoreVert,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (post.canDelete) {
              DropdownMenuItem(
                text = { Text(strings.delete) },
                onClick = { menuOpen = false; onDelete() },
              )
            }
            if (config.features.reportingEnabled) {
              DropdownMenuItem(
                text = { Text(strings.report) },
                onClick = { menuOpen = false; onReport() },
              )
            }
          }
        }
      },
    )

    if (post.isPendingReview) NoticeBanner(strings.pendingReview)

    val body = post.translatedBody ?: post.body
    if (body.isNotBlank()) {
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (expanded) Int.MAX_VALUE else config.limits.feedPreviewLines,
        overflow = TextOverflow.Ellipsis,
      )
      // The button only appears when the text can really overflow: we rely on
      // the preview line count rather than a measurement, which would cost a
      // layout pass per card.
      if (body.lines().size > config.limits.feedPreviewLines || body.length > 280) {
        Text(
          text = if (expanded) strings.seeLess else strings.seeMore,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.clickable { expanded = !expanded },
        )
      }
    }

    post.media.firstOrNull()?.let { media ->
      AsyncImage(
        model = media.url,
        contentDescription = media.alt,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxWidth()
          // The real ratio when the server gives it: that reserves the space
          // before loading, so nothing jumps while scrolling.
          .aspectRatio(
            if (media.width != null && media.height != null && media.height > 0) {
              media.width.toFloat() / media.height.toFloat()
            } else {
              16f / 9f
            },
          )
          .clip(MaterialTheme.shapes.small)
          .background(MaterialTheme.colorScheme.surfaceVariant),
      )
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (config.features.reactionsEnabled) {
        val kind = config.features.reactions.firstOrNull() ?: CommunityReactionKind.LIKE
        val reacted = post.myReaction != null
        CounterAction(
          label = post.likeCount.takeIf { it > 0 }?.toString() ?: strings.like,
          icon = {
            Icon(
              imageVector = if (reacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
              contentDescription = strings.like,
              tint = if (reacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          },
          tint = if (reacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          onClick = { onReact(kind) },
        )
      }

      if (config.features.commentsEnabled) {
        CounterAction(
          label = post.commentCount.takeIf { it > 0 }?.toString() ?: strings.comment,
          icon = { Text("💬", style = MaterialTheme.typography.bodySmall) },
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          onClick = onOpen,
        )
      }

      if (config.features.viewsEnabled && post.viewCount > 0) {
        Text(
          text = "👁 ${post.viewCount}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun CounterAction(
  label: String,
  icon: @Composable () -> Unit,
  tint: Color,
  onClick: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
      .clip(MaterialTheme.shapes.small)
      .clickable(onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 4.dp),
  ) {
    icon()
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
  }
}
