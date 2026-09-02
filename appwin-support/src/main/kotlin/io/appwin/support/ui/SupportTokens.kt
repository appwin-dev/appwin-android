package io.appwin.support.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.support.domain.MessengerDesign
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Messenger design tokens, mirroring `AppwinTokens.swift` on iOS and the
 * Figma messenger frames (441:10486 home, 470:6959 thread).
 *
 * The scale is closed on purpose: the studio drives the accent colour and the
 * corner radius from the dashboard, everything else is fixed so the two
 * platforms cannot drift apart.
 */
internal object SupportTokens {
  val sheetBackground: Color = Color(0xFFF1F5F9)
  val surface: Color = Color.White
  val surfaceMuted: Color = Color(0xFFE2E8F0)
  val textMain: Color = Color(0xFF0E172A)
  val textSecondary: Color = Color(0xFF334156)
  val textTertiary: Color = Color(0xFF94A3B8)
  val border: Color = Color(0xFFE2E8F0)
  val borderSubtle: Color = Color(0xFFF1F5F9)
  val scrim: Color = Color(0x33020617)

  /** Sheet corner radius - the panel the whole messenger lives in. */
  val sheetRadius = 24.dp

  /** Chat bubble corners: three rounded, the tail corner nearly square. */
  val bubbleRadius = 16.dp
  val bubbleTailRadius = 2.dp

  val bannerRadius = 16.dp
  val welcomeRadius = 24.dp

  val sheetPadding = 20.dp
  val sectionGap = 24.dp
  val itemGap = 12.dp
  val bubbleGap = 4.dp
  val cardPadding = 16.dp

  val avatarSize = 20.dp
  val iconSize = 16.dp
  val smallIconSize = 12.dp
  val closeSize = 20.dp

  val titleText = 12.sp
  val greetingText = 22.sp
  val bodyText = 12.sp
  val captionText = 10.sp

  /** Figma `support-banner` 447:5969 - 280x91. */
  const val BANNER_ASPECT: Float = 280f / 91f
}

/**
 * Accent fill honouring the studio's `autoGradient`.
 *
 * Same formula as the dashboard (`messenger-design-utils.ts`) and iOS
 * (`Theme.accentFill`): a 155deg ramp from a 22% darker shade to the accent, so
 * a colour picked once in the dashboard looks identical on the three surfaces.
 */
internal fun accentBrush(design: MessengerDesign, accent: Color): Brush {
  if (!design.autoGradient) return SolidColorBrush(accent)
  return Brush.linearGradient(
    colors = listOf(darken(accent, 0.22f), accent),
    start = androidx.compose.ui.geometry.Offset.Zero,
    end = androidx.compose.ui.geometry.Offset.Infinite,
  )
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
