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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityColorScheme
import io.appwin.community.domain.CommunityConfig
import io.appwin.community.domain.CommunityFontFamily
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
      background = Color(0xFFF8F9FC),
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
    // `inter`, `rounded` and `custom` have no system equivalent on Android, so
    // they fall back to the default font rather than bundling one in the SDK,
    // which every integrating app would pay for. On most devices the platform
    // face (Roboto) is close enough to Inter that the feed still reads as the
    // mock; a studio that wants Inter exactly bundles it and names it under
    // `custom`.
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


/* -------------------------------------------------------------------------- */
/* Surfaces d'accent                                                          */
/* -------------------------------------------------------------------------- */

/**
 * Fill of the active group pill - Figma gradient/brand/Fire at 115.525°.
 */
internal fun accentPillBrush(config: CommunityConfig): Brush = accentBrush(config, 115.525f)

/** Fill of the compose button - same stops, 124.717°. */
internal fun accentComposeBrush(config: CommunityConfig): Brush = accentBrush(config, 124.717f)

/**
 * Drop shadow under the compose button.
 *
 * The mock's shadow is the gradient's dark stop, not a fixed orange: a studio
 * on a blue accent must not get an orange halo under its button.
 */
internal fun accentShadowColor(config: CommunityConfig): Color =
  parseHexColor(shadeHex(config.theme.primaryHex))
    ?: parseHexColor(config.theme.primaryHex)
    ?: DefaultPrimary

private fun accentBrush(config: CommunityConfig, degrees: Float): Brush {
  val accent = parseHexColor(config.theme.primaryHex) ?: DefaultPrimary
  if (!config.theme.autoGradient) return SolidColor(accent)
  val dark = parseHexColor(shadeHex(config.theme.primaryHex)) ?: accent
  return CssAngleGradient(listOf(dark, accent), listOf(0.031f, 0.711f), degrees)
}

/**
 * Linear gradient along a CSS angle.
 *
 * `Brush.linearGradient` only takes fixed offsets, so an angle-driven gradient
 * has to be resolved once the drawn size is known - hence a `ShaderBrush`
 * rather than a plain brush built up front.
 */
private class CssAngleGradient(
  private val colors: List<Color>,
  private val stops: List<Float>,
  private val degrees: Float,
) : ShaderBrush() {
  override fun createShader(size: Size): Shader {
    // CSS convention: 0° points up, angles run clockwise. In a y-down canvas
    // that is the vector (sin θ, −cos θ).
    val rad = Math.toRadians(degrees.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    // Length of the gradient line, so the stops span the whole box like CSS.
    val length = abs(size.width * dx) + abs(size.height * dy)
    val cx = size.width / 2f
    val cy = size.height / 2f
    return LinearGradientShader(
      from = Offset(cx - dx * length / 2f, cy - dy * length / 2f),
      to = Offset(cx + dx * length / 2f, cy + dy * length / 2f),
      colors = colors,
      colorStops = stops,
    )
  }
}

/**
 * Darker stop of the accent gradient.
 *
 * Scaling the RGB channels drags the colour toward black and reads as "colour
 * to black"; here the hue is kept and only the HSL lightness and saturation
 * come down, so #FA7315 lands on the Figma Fire stop rather than a muddy
 * brown. Ratios read off that gradient: L x0.64, S x0.83.
 */
internal fun shadeHex(raw: String): String {
  val hex = raw.trim().removePrefix("#").let { if (it.length == 8) it.substring(2) else it }
  val normalized = when (hex.length) {
    3 -> hex.map { "$it$it" }.joinToString("")
    6 -> hex
    else -> return raw
  }
  val value = normalized.toLongOrNull(16) ?: return raw
  val r = ((value shr 16) and 0xFF) / 255f
  val g = ((value shr 8) and 0xFF) / 255f
  val b = (value and 0xFF) / 255f

  val maxV = max(r, max(g, b))
  val minV = min(r, min(g, b))
  val delta = maxV - minV
  val l = (maxV + minV) / 2f
  if (delta == 0f) return hslToHex(0f, 0f, l * 0.64f)

  val s = delta / (1f - abs(2f * l - 1f))
  val hue = when (maxV) {
    r -> ((g - b) / delta) % 6f
    g -> (b - r) / delta + 2f
    else -> (r - g) / delta + 4f
  }
  return hslToHex((hue * 60f + 360f) % 360f, (s * 0.83f).coerceIn(0f, 1f), (l * 0.64f).coerceIn(0f, 1f))
}

private fun hslToHex(h: Float, s: Float, l: Float): String {
  val c = (1f - abs(2f * l - 1f)) * s
  val x = c * (1f - abs((h / 60f) % 2f - 1f))
  val m = l - c / 2f
  val (r, g, b) = when {
    h < 60f -> Triple(c, x, 0f)
    h < 120f -> Triple(x, c, 0f)
    h < 180f -> Triple(0f, c, x)
    h < 240f -> Triple(0f, x, c)
    h < 300f -> Triple(x, 0f, c)
    else -> Triple(c, 0f, x)
  }
  fun byte(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f).roundToInt()
  return String.format("#%02X%02X%02X", byte(r), byte(g), byte(b))
}
