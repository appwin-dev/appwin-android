package io.appwin.community.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityColorScheme
import io.appwin.community.domain.CommunityConfig
import io.appwin.community.domain.CommunityFontFamily

/**
 * Feed theme, driven by the studio's configuration.
 *
 * Nothing visual is set in the host app's code: accent colour, font, text
 * scale, corner radius and light/dark mode all come from the dashboard. The SDK
 * re-reads the configuration on every open, so a studio-side change applies
 * without republishing the app.
 */
@Composable
internal fun CommunityTheme(
  config: CommunityConfig,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val dark = when (config.theme.colorScheme) {
    CommunityColorScheme.SYSTEM -> systemDark
    CommunityColorScheme.LIGHT -> false
    CommunityColorScheme.DARK -> true
  }

  val primary = parseHexColor(config.theme.primaryHex) ?: DefaultPrimary
  val onPrimary = parseHexColor(config.theme.primaryForegroundHex) ?: Color.White

  // Containers are pulled onto the accent, and that is not a detail: Material 3
  // leaves them on its default purple, and those are what the selected group
  // chip (`secondaryContainer`) and the post button (`primaryContainer`) use.
  // Without these lines the studio's colour applies everywhere EXCEPT the two
  // most visible elements of the feed, and the result diverges from iOS, where
  // the accent is applied uniformly.
  val colors = if (dark) {
    darkColorScheme(
      primary = primary,
      onPrimary = onPrimary,
      primaryContainer = primary,
      onPrimaryContainer = onPrimary,
      secondaryContainer = primary,
      onSecondaryContainer = onPrimary,
      surfaceTint = primary,
      background = Color(0xFF0B0F16),
      surface = Color(0xFF121821),
      surfaceVariant = Color(0xFF1B222D),
      onSurfaceVariant = Color(0xFF9AA7BD),
      outlineVariant = Color(0xFF232B38),
    )
  } else {
    lightColorScheme(
      primary = primary,
      onPrimary = onPrimary,
      primaryContainer = primary,
      onPrimaryContainer = onPrimary,
      secondaryContainer = primary,
      onSecondaryContainer = onPrimary,
      surfaceTint = primary,
      background = Color(0xFFF8FAFC),
      surface = Color.White,
      surfaceVariant = Color(0xFFF1F5F9),
      onSurfaceVariant = Color(0xFF64748B),
      outlineVariant = Color(0xFFE2E8F0),
    )
  }

  val radius = config.theme.radius.dp.dp
  val family = when (config.theme.fontFamily) {
    CommunityFontFamily.SERIF -> FontFamily.Serif
    CommunityFontFamily.MONOSPACE -> FontFamily.Monospace
    // `rounded` and `custom` have no system equivalent on Android, so they fall
    // back to the default font rather than bundling one in the SDK, which every
    // integrating app would pay for.
    else -> FontFamily.Default
  }

  CompositionLocalProvider(LocalCommunityConfig provides config) {
    MaterialTheme(
      colorScheme = colors,
      shapes = Shapes(
        small = androidx.compose.foundation.shape.RoundedCornerShape(radius / 2),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(radius),
        large = androidx.compose.foundation.shape.RoundedCornerShape(radius),
      ),
      typography = scaledTypography(config.theme.fontScale.scale, family),
      content = content,
    )
  }
}

/** Current configuration, readable by any component of the feed. */
internal val LocalCommunityConfig = staticCompositionLocalOf { CommunityConfig() }

private val DefaultPrimary = Color(0xFFF97316)

private fun scaledTypography(scale: Float, family: FontFamily): Typography {
  val base = Typography()
  fun scaleOf(size: Float) = (size * scale).sp
  return base.copy(
    headlineSmall = base.headlineSmall.copy(fontFamily = family, fontSize = scaleOf(22f)),
    titleLarge = base.titleLarge.copy(fontFamily = family, fontSize = scaleOf(20f)),
    titleMedium = base.titleMedium.copy(fontFamily = family, fontSize = scaleOf(16f)),
    titleSmall = base.titleSmall.copy(fontFamily = family, fontSize = scaleOf(14f)),
    bodyLarge = base.bodyLarge.copy(fontFamily = family, fontSize = scaleOf(16f)),
    bodyMedium = base.bodyMedium.copy(fontFamily = family, fontSize = scaleOf(14f)),
    bodySmall = base.bodySmall.copy(fontFamily = family, fontSize = scaleOf(12f)),
    labelLarge = base.labelLarge.copy(fontFamily = family, fontSize = scaleOf(14f)),
    labelMedium = base.labelMedium.copy(fontFamily = family, fontSize = scaleOf(12f)),
    labelSmall = base.labelSmall.copy(fontFamily = family, fontSize = scaleOf(11f)),
  )
}

/**
 * `#RGB`, `#RRGGBB` or `#AARRGGBB` to a colour.
 *
 * Returns `null` on an unreadable value, so the caller falls back to the default
 * accent: a colour mistyped in the studio must not make the buttons transparent.
 */
internal fun parseHexColor(raw: String?): Color? {
  val hex = raw?.trim()?.removePrefix("#") ?: return null
  val normalized = when (hex.length) {
    3 -> hex.map { "$it$it" }.joinToString("")
    6, 8 -> hex
    else -> return null
  }
  val value = normalized.toLongOrNull(16) ?: return null
  return if (normalized.length == 6) Color(value or 0xFF000000L) else Color(value)
}
