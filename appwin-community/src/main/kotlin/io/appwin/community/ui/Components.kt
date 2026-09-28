package io.appwin.community.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.appwin.community.domain.CommunityAuthor

/**
 * Shared feed components.
 *
 * Grouped here rather than duplicated screen by screen: an avatar or an empty
 * state that diverges between the feed and a post's detail is noticed
 * immediately, and has to be fixed in two places.
 */

/** Muted meta text (dates, inactive actions) - matches iOS `textTertiary`. */
internal val CommunityTertiary: Color
  get() = CommunityColors.textTertiary

@Composable
internal fun CommunityAvatar(
  url: String?,
  fallbackText: String,
  size: Int = 40,
  modifier: Modifier = Modifier,
) {
  val shape = CircleShape
  val accent = MaterialTheme.colorScheme.primary
  val context = LocalContext.current
  Box(
    modifier = modifier
      .size(size.dp)
      .clip(shape)
      .background(accent.copy(alpha = 0.15f)),
    contentAlignment = Alignment.Center,
  ) {
    if (url.isNullOrBlank()) {
      Text(
        text = initialsOf(fallbackText),
        fontSize = (size * 0.36f).sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
      )
    } else {
      AsyncImage(
        model = ImageRequest.Builder(context)
          .data(url)
          // Stable key so 40dp feed cells and 72dp profile headers share the
          // same memory entry; Coil then downscales for the smaller size.
          .memoryCacheKey(url)
          .diskCacheKey(url)
          .crossfade(false)
          .build(),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(shape),
      )
    }
  }
}

private fun initialsOf(nickname: String): String {
  val parts = nickname.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
  val letters = parts.mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.joinToString("")
  return letters.ifEmpty { "?" }
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
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
  ) {
    CommunityAvatar(author?.avatarUrl, author?.nickname.orEmpty())

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = author?.nickname.orEmpty(),
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          color = CommunityColors.textPrimary,
        )
        if (author?.isTeam == true) TeamBadge(strings.teamBadge)
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = CommunityColors.textTertiary,
      )
    }

    trailing?.invoke()
  }
}

@Composable
internal fun TeamBadge(label: String) {
  Text(
    text = label,
    fontSize = 10.sp,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onPrimary,
    modifier = Modifier
      .clip(RoundedCornerShape(50))
      .background(MaterialTheme.colorScheme.primary)
      .padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

/** Information banner, used for moderation and sanctions. */
@Composable
internal fun NoticeBanner(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    fontSize = 12.sp,
    color = CommunityColors.textSecondary,
    modifier = modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.small)
      .background(CommunityColors.warning.copy(alpha = 0.12f))
      .padding(8.dp),
  )
}

@Composable
internal fun CommunityEmptyState(
  icon: ImageVector,
  title: String,
  message: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = CommunityColors.textTertiary,
      modifier = Modifier.size(40.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = CommunityColors.textPrimary,
      modifier = Modifier.padding(top = 16.dp),
    )
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = CommunityColors.textTertiary,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 6.dp),
    )
    if (actionLabel != null && onAction != null) {
      Button(
        onClick = onAction,
        modifier = Modifier.padding(top = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      ) {
        Text(actionLabel, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

/** Relative age, worded from the device locale via [CommunityStrings]. */
internal fun relativeTime(
  millis: Long,
  strings: CommunityStrings,
  nowMillis: Long = System.currentTimeMillis(),
): String {
  if (millis <= 0) return ""
  val seconds = ((nowMillis - millis) / 1000).coerceAtLeast(0)
  return when {
    seconds < 60 -> strings.relativeNow()
    seconds < 3_600 -> strings.relativeMinutes((seconds / 60).toInt())
    seconds < 86_400 -> strings.relativeHours((seconds / 3_600).toInt())
    seconds < 604_800 -> strings.relativeDays((seconds / 86_400).toInt())
    else -> strings.relativeWeeks((seconds / 604_800).toInt())
  }
}
