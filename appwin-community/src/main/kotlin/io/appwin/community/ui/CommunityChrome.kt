package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Screen chrome shared by the community screens: round glass buttons and the text nav bar.

/**
 * Figma button/icon-native: translucent surface, soft halo, 17dp glyph (iOS).
 */
@Composable
internal fun GlassIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(38.dp)
      .shadow(20.dp, CircleShape, ambientColor = GlassHalo, spotColor = GlassHalo)
      .clip(CircleShape)
      .background(CommunityColors.surface.copy(alpha = 0.56f))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = CommunityColors.textPrimary,
      modifier = Modifier.size(17.dp),
    )
  }
}

private val GlassHalo = Color.Black.copy(alpha = 0.12f)

/**
 * Bar of the pushed screens - Figma `header/default` (19:5365): a subtle text
 * action on the left ("Retour", "Annuler"), the title centred, an optional
 * trailing action, on the container colour.
 */
@Composable
internal fun TextNavBar(
  title: String,
  leadingLabel: String,
  onLeading: () -> Unit,
  trailing: (@Composable () -> Unit)? = null,
  /** The comments bar sits on the container; the composer and editor on the page. */
  containerColor: Color = CommunityColors.surface,
) {
  // 14sp and 16sp labels centred vertically sit on different baselines (Figma
  // aligns them on the text baseline); equal-weight side slots keep the title
  // centred whatever the leading and trailing widths.
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(containerColor)
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 24.dp),
  ) {
    Box(modifier = Modifier.weight(1f).alignByBaseline()) {
      Text(
        text = leadingLabel,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = CommunityColors.textTertiary,
        modifier = Modifier.clickable(onClick = onLeading),
      )
    }
    Text(
      text = title,
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.alignByBaseline().widthIn(max = 200.dp),
    )
    Box(
      modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
      contentAlignment = Alignment.CenterEnd,
    ) { trailing?.invoke() }
  }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
  GlassIconButton(CommunityIcons.ChevronLeft, contentDescription = null, onClick = onBack)
}
