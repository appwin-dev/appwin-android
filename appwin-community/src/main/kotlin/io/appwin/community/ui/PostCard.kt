package io.appwin.community.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
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
@OptIn(ExperimentalFoundationApi::class)
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
  onVote: ((String) -> Unit)? = null,
) {
  var expanded by remember(post.id) { mutableStateOf(false) }
  var menuOpen by remember(post.id) { mutableStateOf(false) }
  var reactionPickerOpen by remember(post.id) { mutableStateOf(false) }
  val reactions = config.features.reactions.ifEmpty { listOf(CommunityReactionKind.LIKE) }
  val defaultKind = reactions.first()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .background(MaterialTheme.colorScheme.surface)
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
      onDelete = onDelete,
      onReport = onReport,
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

    post.poll?.let { poll ->
      PostPollBlock(poll = poll, onVote = { onVote?.invoke(it) })
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
            val emoji = post.myReaction?.emoji ?: "❤️"
            Text(
              text = "$emoji ${post.likeCount}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text(
              text = strings.beFirstToReact,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Box {
      Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (config.features.reactionsEnabled) {
          val reacted = post.myReaction != null
          val tint =
            if (reacted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
          CounterAction(
            label = strings.like,
            icon = {
              Icon(
                imageVector = if (reacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = strings.like,
                tint = tint,
                modifier = Modifier.size(18.dp),
              )
            },
            tint = tint,
            onClick = { onReact(defaultKind) },
            onLongClick = {
              if (reactions.size > 1) reactionPickerOpen = true
            },
          )
        }

        if (config.features.commentsEnabled) {
          CounterAction(
            label = strings.comment,
            icon = { Text("\uD83D\uDCAC", style = MaterialTheme.typography.bodyMedium) },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onOpen,
          )
        }
      }

      if (reactionPickerOpen) {
        Surface(
          shape = RoundedCornerShape(24.dp),
          tonalElevation = 4.dp,
          shadowElevation = 8.dp,
          modifier = Modifier
            .align(Alignment.TopStart)
            .offset(y = (-52).dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            reactions.forEach { kind ->
              Text(
                text = kind.emoji,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    onReact(kind)
                    reactionPickerOpen = false
                  }
                  .padding(6.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PostAuthorHeader(
  post: CommunityPost,
  strings: CommunityStrings,
  config: CommunityConfig,
  onOpenProfile: (String) -> Unit,
  menuOpen: Boolean,
  onMenuOpenChange: (Boolean) -> Unit,
  onDelete: () -> Unit,
  onReport: () -> Unit,
) {
  val time = relativeTime(post.publishedAtMillis, strings)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    CommunityAvatar(
      post.author?.avatarUrl,
      post.author?.nickname.orEmpty(),
      modifier = Modifier.then(
        if (post.author?.id != null) {
          Modifier.clickable { onOpenProfile(post.author!!.id) }
        } else {
          Modifier
        },
      ),
    )

    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = post.author?.nickname.orEmpty(),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (post.hasAdminTag) {
          TeamBadge(strings.teamBadge)
        }
        if (time.isNotEmpty()) {
          Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (post.groupName.isNotBlank()) {
        Text(
          text = post.groupName,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Box {
      IconButton(onClick = { onMenuOpenChange(true) }, modifier = Modifier.size(32.dp)) {
        Icon(
          Icons.Default.MoreVert,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
        if (post.canDelete) {
          DropdownMenuItem(
            text = { Text(strings.delete) },
            onClick = { onMenuOpenChange(false); onDelete() },
          )
        }
        if (config.features.reportingEnabled) {
          DropdownMenuItem(
            text = { Text(strings.report) },
            onClick = { onMenuOpenChange(false); onReport() },
          )
        }
      }
    }
  }
}

@Composable
private fun PostPollBlock(
  poll: io.appwin.community.domain.CommunityPoll,
  onVote: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    poll.options.forEach { option ->
      val selected = poll.myOptionId == option.id
      val ratio =
        if (poll.totalVotes > 0) option.voteCount.toFloat() / poll.totalVotes.toFloat() else 0f
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .then(
            if (poll.myOptionId == null) Modifier.clickable { onVote(option.id) } else Modifier,
          ),
      ) {
        if (poll.myOptionId != null) {
          Box(
            modifier = Modifier
              .matchParentSize()
              .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
              .background(
                MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.35f else 0.15f),
              ),
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = option.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
          )
          if (poll.myOptionId != null) {
            Text(
              text = option.voteCount.toString(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
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
  icon: @Composable () -> Unit,
  tint: Color,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
      .clip(MaterialTheme.shapes.small)
      .then(
        if (onLongClick != null) {
          Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        } else {
          Modifier.clickable(onClick = onClick)
        },
      )
      .padding(vertical = 6.dp),
  ) {
    icon()
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
  }
}
