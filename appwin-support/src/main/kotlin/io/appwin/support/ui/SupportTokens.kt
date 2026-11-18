package io.appwin.support.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.support.domain.MessengerDesign
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Messenger design tokens, mirroring `AppwinTokens.swift` on iOS and the
 * Figma InApp frames (support-home 40:6390, support-convo 40:6956).
 *
 * The studio drives the accent, the corner radius, the colour scheme and the
 * gray warmth from the dashboard. The grays below therefore resolve from
 * [LocalSupportGrays] (set by [SupportTheme]); the rest is fixed so the
 * platforms cannot drift apart.
 */
internal object SupportTokens {
  /** Figma bg/page: the panel behind the cards. */
  val sheetBackground: Color @Composable @ReadOnlyComposable get() = LocalSupportGrays.current.page

  /** Figma bg/container: the cards. */
  val surface: Color @Composable @ReadOnlyComposable get() = LocalSupportGrays.current.surface

  /** Figma bg/low: info strip, chips, skeletons. */
  val surfaceMuted: Color @Composable @ReadOnlyComposable get() = LocalSupportGrays.current.raised
  val textMain: Color @Composable @ReadOnlyComposable get() = LocalSupportGrays.current.text
  val textSecondary: Color @Composable @ReadOnlyComposable get() = LocalSupportGrays.current.muted
  val textTertiary: Color @Composable @ReadOnlyComposable get() = LocalSupportGrays.current.subtle
  val border: Color @Composable @ReadOnlyComposable get() = LocalSupportGrays.current.border
  val scrim: Color = Color(0x33020617)

  /** Figma shadow/low: agent bubble, composer, brand CTA. */
  val shadowLow: Color = Color(0x14171717)


  /** Sheet corner radius - the panel the whole messenger lives in. */
  val sheetRadius = 24.dp

  /**
   * The tail corner of a chat bubble, nearly square. The other corners, the
   * banner and the cards follow the studio's radius (`design.radius`).
   */
  val bubbleTailRadius = 2.dp

  /** Figma support-home: header padded 16, content gutters 20, blocks 40 apart. */
  val headerPadding = 16.dp
  val sheetPadding = 20.dp
  val pageGap = 16.dp
  val sectionGap = 40.dp
  val itemGap = 12.dp
  val bubbleGap = 4.dp
  val glassButtonSize = 38.dp

  /**
   * Vertical gap inside a burst - two messages from the same author, same minute.
   *
   * Tighter than [itemGap], which separates one burst from the next: the gap is
   * what says "still the same person, still talking", and at 12dp three quick
   * messages read as three separate turns.
   */
  val runGap = 4.dp

  /** Cards (bg/container) are padded 20; bubbles and the info strip 16. */
  val cardPadding = 20.dp
  val bubblePadding = 16.dp

  // Figma InApp is drawn at device scale (393pt): these are its values, not
  // the dashboard preview's scaled-down ones.
  val avatarSize = 24.dp
  val iconSize = 20.dp
  val smallIconSize = 16.dp
  val closeSize = 20.dp

  val titleText = 16.sp
  val greetingText = 32.sp
  val faqTitleText = 24.sp
  val bodyText = 14.sp
  val articleText = 15.sp
  val labelText = 12.sp
  val captionText = 10.sp

  /** Figma `support-banner` 447:5969 - 280x91. */
  const val BANNER_ASPECT: Float = 280f / 91f
}

/**
 * Accent fill honouring the studio's `autoGradient`.
 *
 * Same stops as the dashboard (`brandButtonStyle`) and iOS
 * (`Theme.accentFill`): the studio's second colour, else a lighter tint of the
 * accent, ramping into the accent.
 */
internal fun accentBrush(design: MessengerDesign, accent: Color): Brush {
  if (!design.autoGradient) return SolidColorBrush(accent)
  // CSS `#RRGGBBAA` puts alpha last, `parseHexColor` reads it first: keep RGB.
  val start = design.gradientHex?.trim()?.removePrefix("#")?.take(6)?.let(::parseHexColor)
    ?: gradientTint(accent)
  return Brush.linearGradient(
    colors = listOf(start, accent),
    start = androidx.compose.ui.geometry.Offset.Zero,
    end = androidx.compose.ui.geometry.Offset.Infinite,
  )
}

/**
 * Default first stop of the brand gradient: a lighter, livelier tint. Same
 * arithmetic as `gradientTintHex` in the dashboard (#F2A6F6 -> #FDD3FF): the
 * HSL lightness closes 55 % of its gap to white, the saturation gains a
 * quarter.
 */
internal fun gradientTint(color: Color): Color {
  val r = color.red
  val g = color.green
  val b = color.blue
  val maxV = maxOf(r, g, b)
  val minV = minOf(r, g, b)
  val delta = maxV - minV
  val l = (maxV + minV) / 2f
  var h = 0f
  var s = 0f
  if (delta > 0f) {
    s = delta / (1f - kotlin.math.abs(2f * l - 1f))
    h = when (maxV) {
      r -> ((g - b) / delta) % 6f
      g -> (b - r) / delta + 2f
      else -> (r - g) / delta + 4f
    }
    h = (h * 60f + 360f) % 360f
  }
  val s2 = (s * 1.25f).coerceAtMost(1f)
  val l2 = (l + (1f - l) * 0.55f).coerceAtMost(1f)
  val c = (1f - kotlin.math.abs(2f * l2 - 1f)) * s2
  val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
  val m = l2 - c / 2f
  val (r1, g1, b1) = when {
    h < 60f -> Triple(c, x, 0f)
    h < 120f -> Triple(x, c, 0f)
    h < 180f -> Triple(0f, c, x)
    h < 240f -> Triple(0f, x, c)
    h < 300f -> Triple(x, 0f, c)
    else -> Triple(c, 0f, x)
  }
  fun channel(v: Float): Float = (((v + m).coerceIn(0f, 1f) * 255f).roundToInt()) / 255f
  return Color(channel(r1), channel(g1), channel(b1), color.alpha)
}

/** A [Brush] for a flat colour, so callers can treat both cases alike. */
internal fun SolidColorBrush(color: Color): Brush = Brush.linearGradient(listOf(color, color))

/**
 * Darkens a colour by [amount], matching `darkenHex` in the dashboard.
 *
 * Multiplies the 8-bit channels rather than working in a perceptual space: the
 * dashboard preview must land on exactly the same pixel, and it does it this way.
 */
internal fun darken(color: Color, amount: Float = 0.22f): Color {
  fun channel(v: Float): Float =
    ((v * 255f) * (1f - amount)).roundToInt().coerceIn(0, 255) / 255f
  return Color(channel(color.red), channel(color.green), channel(color.blue), color.alpha)
}

/**
 * Black or white, whichever stays readable on [background].
 *
 * Fallback for a studio that set an accent but no foreground: white on yellow
 * is the kind of thing you only notice in production.
 */
internal fun contrastingForeground(background: Color): Color {
  fun linear(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
  }
  val luminance =
    0.2126 * linear(background.red) + 0.7152 * linear(background.green) +
      0.0722 * linear(background.blue)
  return if (luminance > 0.5) Color.Black else Color.White
}

/**
 * `#RGB`, `#RRGGBB` or `#AARRGGBB` to a colour, `null` when unreadable.
 *
 * The caller falls back to the default accent: a colour mistyped in the studio
 * must not make the buttons transparent.
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
