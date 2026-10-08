package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityProfile


// Feed header, compose button and group pills (Figma feed 5:1837).

/**
 * The studio's feed title, or none: a blank `headerTitle` shows no title at all
 * (no project-name fallback), and `headerTitleVisible` hides it.
 */
internal fun feedTitle(config: io.appwin.community.domain.CommunityConfig): String? =
  config.theme.headerTitle?.trim()?.takeIf { it.isNotEmpty() && config.theme.headerTitleVisible }

/**
 * Screen header - Figma feed `header` (5:1983): 32sp title and the member's
 * avatar on bg/container, no divider (the list scrolls under the elevated bar).
 */
@Composable
internal fun FeedHeader(
  title: String?,
  closeLabel: String,
  profileLabel: String,
  profile: io.appwin.community.domain.CommunityProfile?,
  onClose: (() -> Unit)?,
  onOpenProfile: () -> Unit,
  unreadSanctionCount: Int = 0,
  /** `null` for a plain member: no flag. */
  moderationPendingCount: Int? = null,
  onOpenSanctions: () -> Unit = {},
  onOpenModeration: () -> Unit = {},
  strings: CommunityStrings? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      // Custom header (not Material TopAppBar) must pad the status bar itself
      // so the title / avatar sit below the cutout on edge-to-edge windows.
      .statusBarsPadding()
      .padding(start = 20.dp, end = 20.dp, top = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (title != null) {
      Text(
        text = title,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Medium,
        color = CommunityColors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
    } else {
      Spacer(modifier = Modifier.weight(1f))
    }
    if (onClose != null) {
      GlassIconButton(Icons.Default.Close, contentDescription = closeLabel, onClick = onClose)
    }
    // The bell only while sanctions wait to be read (178:3385); the flag for moderators (178:2735).
    if (unreadSanctionCount > 0) {
      BadgedIcon(SolarModerationIcons.Bell, unreadSanctionCount, strings?.openSanctions, onOpenSanctions)
    }
    if (moderationPendingCount != null) {
      BadgedIcon(SolarModerationIcons.Flag, moderationPendingCount, strings?.openModeration, onOpenModeration)
    }
    // Same trailing slot as iOS: member avatar opens their own profile.
    // Always render (placeholder before bootstrap) so RN/Flutter match native.
    val headerProfile = profile ?: CommunityProfile.Placeholder
    CommunityAvatar(
      url = headerProfile.avatarUrl,
      fallbackText = headerProfile.nickname.ifBlank { "?" },
      size = 40,
      modifier = Modifier
        .clip(CircleShape)
        .clickable(
          enabled = headerProfile.id.isNotEmpty(),
          onClick = onOpenProfile,
          onClickLabel = profileLabel,
        ),
    )
  }
}

/**
 * Compose button - Figma "Créer un post" (5:1999): accent pill, white 12%
 * ring, pen glyph, lifted by the neutral shadow/high.
 */
@Composable
internal fun ComposeButton(
  label: String,
  brush: Brush,
  shadow: Color?,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(LocalCommunityConfig.current.radiusCard)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      .height(56.dp)
      // Figma 0 24 40: a tall, soft halo. Tinted shadows need API 28; below it
      // Android draws its default grey one, which is close enough.
      .then(
        if (shadow != null) {
          Modifier.shadow(24.dp, shape, ambientColor = shadow, spotColor = shadow)
        } else {
          Modifier
        },
      )
      .clip(shape)
      .background(brush)
      .border(3.dp, Color.White.copy(alpha = 0.12f), shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp),
  ) {
    Icon(
      SolarIcons.PenNewSquare,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = label,
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onPrimary,
    )
  }
}

/**
 * Group pills - Figma `toggleGroup` (5:1989): the active pill on the accent
 * gradient, the others outlined.
 *
 * Hand-rolled rather than `FilterChip`: a Material chip only takes a flat
 * container colour.
 */
@Composable
internal fun GroupTabs(
  groups: List<io.appwin.community.domain.CommunityGroup>,
  selectedId: String?,
  allLabel: String,
  brush: Brush,
  onSelect: (String?) -> Unit,
) {
  LazyRow(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(20.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    item {
      GroupPill(allLabel, selectedId == null, brush) { onSelect(null) }
    }
    items(groups, key = { it.id }) { group ->
      GroupPill(
        label = listOfNotNull(group.emoji, group.name).joinToString(" "),
        selected = selectedId == group.id,
        brush = brush,
      ) { onSelect(group.id) }
    }
  }
}

@Composable
private fun GroupPill(
  label: String,
  selected: Boolean,
  brush: Brush,
  onClick: () -> Unit,
) {
  // Same radius token as the cards: LOW gives soft rectangles, MAX capsules.
  val shape = RoundedCornerShape(LocalCommunityConfig.current.radiusCard)
  val base = Modifier.clip(shape)
  val filled = if (selected) {
    base.background(brush)
  } else {
    base.border(1.dp, CommunityColors.border, shape)
  }
  Text(
    text = label,
    fontSize = 14.sp,
    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
    color = if (selected) MaterialTheme.colorScheme.onPrimary else CommunityColors.textTertiary,
    maxLines = 1,
    modifier = filled
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp),
  )
}


/** Header glyph with its red counter: the bell and the flag. */
@Composable
private fun BadgedIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, label: String?, onClick: () -> Unit) {
  Box(
    modifier = Modifier.size(width = 32.dp, height = 40.dp).clickable(onClickLabel = label, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = label, tint = CommunityColors.textPrimary, modifier = Modifier.size(24.dp))
    if (count > 0) {
      CountBadge(count, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = 2.dp))
    }
  }
}
