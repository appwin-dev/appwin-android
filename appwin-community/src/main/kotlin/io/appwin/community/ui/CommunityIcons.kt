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

  /** Edit badge on the profile avatar. */
  val Pencil: ImageVector by lazy {
    strokeIcon(
      "AppwinPencil",
      "M14.3601 4.07866L15.2869 3.15178C16.8226 1.61607 19.3125 1.61607 20.8482 3.15178C22.3839 " +
        "4.68748 22.3839 7.17735 20.8482 8.71306L19.9213 9.63993M14.3601 4.07866C14.3601 4.07866 " +
        "14.4759 6.04828 16.2138 7.78618C17.9517 9.52407 19.9213 9.63993 19.9213 9.63993M14.3601 " +
        "4.07866L5.83882 12.5999C5.26166 13.1771 4.97308 13.4656 4.7249 13.7838C4.43213 14.1592 " +
        "4.18114 14.5653 3.97634 14.995C3.80273 15.3593 3.67368 15.7465 3.41556 16.5208L2.32181 " +
        "19.8021M19.9213 9.63993L11.4001 18.1612C10.8229 18.7383 10.5344 19.0269 10.2162 " +
        "19.2751C9.84082 19.5679 9.43469 19.8189 9.00498 20.0237C8.6407 20.1973 8.25352 20.3263 " +
        "7.47918 20.5844L4.19792 21.6782M4.19792 21.6782L3.39584 21.9456C3.01478 22.0726 2.59466 " +
        "21.9734 2.31063 21.6894C2.0266 21.4053 1.92743 20.9852 2.05445 20.6042L2.32181 " +
        "19.8021M4.19792 21.6782L2.32181 19.8021",
    )
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
