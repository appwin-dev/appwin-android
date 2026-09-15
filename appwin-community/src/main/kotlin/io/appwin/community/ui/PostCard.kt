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
import androidx.compose.material3.HorizontalDivider
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
      .clip(MaterialTheme.shapes.medium)
      .background(MaterialTheme.colorScheme.surface)
      .clickable(onClick = onOpen)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    AuthorRow(
      author = post.author,
      subtitle = buildString {
        append(post.groupName)
        val time = relativeTime(post.publishedAtMillis, strings)
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
        style = MaterialTheme.typography.bodyLarge,
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

    // Figma `views-row` (2120:24376): views alone, right-aligned, above the rule.
    if (config.features.viewsEnabled && post.viewCount > 0) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Emoji rather than a vector: the eye and the speech bubble live in
        // `material-icons-extended`, and pulling that in would grow every
        // integrating app by megabytes for two glyphs.
        Text(
          text = "\uD83D\uDC41 ${post.viewCount}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Figma `card-actions` (2120:24381). The counts live on the buttons: the
    // mock reads "451 Likes", and printing the same number twice per card was
    // the old layout's real flaw, not its spacing.
    Row(
      horizontalArrangement = Arrangement.spacedBy(24.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (config.features.reactionsEnabled) {
        val kind = config.features.reactions.firstOrNull() ?: CommunityReactionKind.LIKE
        val reacted = post.myReaction != null
        val tint =
          if (reacted) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant
        CounterAction(
          label = strings.likeCount(post.likeCount),
          icon = {
            Icon(
              imageVector = if (reacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
              contentDescription = strings.like,
              tint = tint,
              modifier = Modifier.size(18.dp),
            )
          },
          tint = tint,
          onClick = { onReact(kind) },
        )
      }

      if (config.features.commentsEnabled) {
        CounterAction(
          label = strings.commentCount(post.commentCount),
          icon = { Text("\uD83D\uDCAC", style = MaterialTheme.typography.bodyMedium) },
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          onClick = onOpen,
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
      .padding(vertical = 6.dp),
  ) {
    icon()
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
  }
}
