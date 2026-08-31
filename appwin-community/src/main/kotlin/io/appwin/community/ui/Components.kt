package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.appwin.community.domain.CommunityAuthor

/**
 * Shared feed components.
 *
 * Grouped here rather than duplicated screen by screen: an avatar or an empty
 * state that diverges between the feed and a post's detail is noticed
 * immediately, and has to be fixed in two places.
 */

@Composable
internal fun CommunityAvatar(
  url: String?,
  fallbackText: String,
  size: Int = 40,
  modifier: Modifier = Modifier,
) {
  val shape = CircleShape
  Box(
    modifier = modifier
      .size(size.dp)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    if (url.isNullOrBlank()) {
      // An initial rather than a generic silhouette: in a feed it helps
      // recognise a regular at a glance.
      Text(
        text = fallbackText.trim().take(1).uppercase().ifEmpty { "?" },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().clip(shape),
      )
    }
  }
}

@Composable
internal fun AuthorRow(
  author: CommunityAuthor?,
  subtitle: String,
  strings: CommunityStrings,
  onClick: (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
  ) {
    CommunityAvatar(author?.avatarUrl, author?.nickname.orEmpty())

    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = author?.nickname.orEmpty(),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (author?.isTeam == true) TeamBadge(strings.teamBadge)
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    trailing?.invoke()
  }
}

@Composable
internal fun TeamBadge(label: String) {
  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
      .padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

/** Information banner, used for moderation and sanctions. */
@Composable
internal fun NoticeBanner(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.small)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .border(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
        MaterialTheme.shapes.small,
      )
      .padding(horizontal = 12.dp, vertical = 8.dp),
  )
}

@Composable
internal fun CommunityEmptyState(
  icon: ImageVector,
  title: String,
  message: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(40.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 16.dp),
    )
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 6.dp),
    )
    if (actionLabel != null && onAction != null) {
      TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
        Text(actionLabel)
      }
    }
  }
}

/**
 * Relative age, with no formatting dependency.
 *
 * `DateUtils.getRelativeTimeSpanString` would do the job but produces wording
 * that varies a lot across Android versions; in a feed, consistency wins.
 */
internal fun relativeTime(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
  if (millis <= 0) return ""
  val seconds = ((nowMillis - millis) / 1000).coerceAtLeast(0)
  return when {
    seconds < 60 -> "now"
    seconds < 3_600 -> "${seconds / 60}m"
    seconds < 86_400 -> "${seconds / 3_600}h"
    seconds < 604_800 -> "${seconds / 86_400}d"
    else -> "${seconds / 604_800}w"
  }
}
