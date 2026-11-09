package io.appwin.community.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Feed colour tokens. The grays resolve from [LocalCommunityGrays] (scheme +
 * gray warmth, set by [CommunityTheme]); brand and status colours are fixed.
 */
internal object CommunityColors {
  /** Same hex as iOS `AppwinCommunityPalette.brand` / default accent. */
  val brand = Color(0xFFFA7315)
  val textPrimary: Color @Composable @ReadOnlyComposable get() = LocalCommunityGrays.current.text
  val textSecondary: Color @Composable @ReadOnlyComposable get() = LocalCommunityGrays.current.muted
  val textTertiary: Color @Composable @ReadOnlyComposable get() = LocalCommunityGrays.current.subtle
  val border: Color @Composable @ReadOnlyComposable get() = LocalCommunityGrays.current.border

  /** Figma bg/container: the cards. */
  val surface: Color @Composable @ReadOnlyComposable get() = LocalCommunityGrays.current.surface

  /** Figma bg/page: the screen behind the cards. */
  val background: Color @Composable @ReadOnlyComposable get() = LocalCommunityGrays.current.page

  /** Figma bg/low: poll options, comment bubbles, chips. */
  val surfaceMuted: Color @Composable @ReadOnlyComposable get() = LocalCommunityGrays.current.raised
  val warning = Color(0xFFF5A623)

  /** Same hex as iOS `AppwinCommunityPalette.danger`. */
  val danger = Color(0xFFE11D48)

  /** Figma filled heart of a liked post or comment (iOS `AppwinCommunityPalette.like`). */
  val like = Color(0xFFE71919)

  /** Figma shadow/low. */
  val shadowLow = Color(0x14171717)

  // Moderation (Figma special/*), fixed whatever the studio's theme.

  /** special/alert: deletion, ban, the counter badges. */
  val alert = Color(0xFFBE123C)
  val alertSoft = Color(0xFFFFE4E6)

  /** special/warning: hiding, shadow ban, warning, reports. */
  val caution = Color(0xFFD97706)
  val cautionSoft = Color(0xFFFEF3C7)

  /** special/indigo: the admin badge on an avatar. */
  val adminBadge = Color(0xFF4F46E5)

  /** bg/invert: « J'ai compris ». */
  val invert = Color(0xFF1E293B)
}

/** Glyphs with no Solar counterpart in the Figma frames; the Solar set lives in [SolarIcons]. */
internal object CommunityIcons {

  private const val BUBBLE_PATH =
    "M12 20.25C16.9706 20.25 21 16.5563 21 12C21 7.44365 16.9706 3.75 12 3.75" +
      "C7.02944 3.75 3 7.44365 3 12C3 14.1036 3.85891 16.0234 5.2728 17.4806" +
      "C5.70538 17.9265 6.01357 18.5192 5.85933 19.121C5.68829 19.7883 5.368 20.3959" +
      " 4.93579 20.906C5.0918 20.9339 5.25 20.9558 5.40967 20.9713C5.60376 20.9903" +
      " 5.80078 21 6 21C7.28201 21 8.47016 20.5979 9.44517 19.9129C10.2551 20.1323" +
      " 11.1125 20.25 12 20.25Z"

  /**
   * Two filled bubbles - mirrors SF `bubble.left.and.bubble.right.fill`.
   *
   * The back bubble is translucent rather than cut out: the glyph sits on a
   * gradient, so there is no solid colour to punch the gap with.
   */
  val Conversation: ImageVector by lazy {
    ImageVector.Builder(
      name = "AppwinConversation",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      val bubble = PathParser().parsePathString(BUBBLE_PATH).toNodes()
      addGroup(scaleX = -0.66f, scaleY = 0.66f, translationX = 23.4f, translationY = 0.6f)
      addPath(pathData = bubble, fill = SolidColor(Color.Black), fillAlpha = 0.55f)
      clearGroup()
      addGroup(scaleX = 0.74f, scaleY = 0.74f, translationX = 0.3f, translationY = 5.6f)
      addPath(pathData = bubble, fill = SolidColor(Color.Black))
      clearGroup()
    }.build()
  }

  val Ellipsis: ImageVector by lazy {
    ImageVector.Builder(
      name = "AppwinEllipsis",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      listOf(6f, 12f, 18f).forEach { cx ->
        addPath(
          pathData = PathParser().parsePathString(
            "M${cx + 1.15f},12 a1.15,1.15 0 1,1 -2.3,0 a1.15,1.15 0 1,1 2.3,0",
          ).toNodes(),
          fill = SolidColor(Color.Black),
        )
      }
    }.build()
  }

  val Pin: ImageVector by lazy {
    ImageVector.Builder(
      name = "AppwinPin",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      addPath(
        pathData = PathParser().parsePathString(
          "M16 3a1 1 0 0 1 1 1v.5a4 4 0 0 1-1.17 2.83L14.5 8.66A2 2 0 0 0 14 " +
            "10.07V12l4.4 3.3a1 1 0 0 1 .35.76V17a1 1 0 0 1-1 1h-4.5v3a1 1 0 1 1-2 " +
            "0v-3H6.75a1 1 0 0 1-1-1v-.94a1 1 0 0 1 .35-.76L10.5 12v-1.93a2 2 0 0 " +
            "0-.5-1.41l-1.33-1.33A4 4 0 0 1 7.5 4.5V4a1 1 0 0 1 1-1z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
      )
    }.build()
  }

  val ChevronLeft: ImageVector by lazy { strokeIcon("AppwinChevronLeft", "M15 5L8 12L15 19") }

  private fun strokeIcon(name: String, data: String): ImageVector =
    ImageVector.Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      addPath(
        pathData = PathParser().parsePathString(data).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      )
    }.build()
}
