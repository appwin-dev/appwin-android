package io.appwin.community.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Feed colour tokens locked to iOS light (`AppwinCommunityPalette`).
 *
 * Material3 defaults are close but not identical - using them next to iOS makes
 * the same post look like two products.
 */
internal object CommunityColors {
  /** Same hex as iOS `AppwinCommunityPalette.brand` / default accent. */
  val brand = Color(0xFFFA7315)
  val textPrimary = Color(0xFF0E172A)
  val textSecondary = Color(0xFF334156)
  val textTertiary = Color(0xFF94A3B8)
  val border = Color(0xFFE2E8F0)
  val surface = Color.White
  val background = Color(0xFFF8F9FC)
  val surfaceMuted = Color(0xFFF1F5F9)
  val warning = Color(0xFFF5A623)
}

/**
 * Stroke glyphs for the post card, closer to SF Symbols than Material Icons.
 *
 * Material `Favorite` / `ChatBubbleOutline` sit on a heavier grid and read as a
 * different app next to the iOS feed.
 */
internal object CommunityIcons {

  val Heart: ImageVector by lazy {
    strokeIcon(
      "AppwinHeart",
      "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 " +
        "7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z",
    )
  }

  val HeartFilled: ImageVector by lazy {
    ImageVector.Builder(
      name = "AppwinHeartFilled",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      addPath(
        pathData = PathParser().parsePathString(
          "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 " +
            "7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
      )
    }.build()
  }

  /** Oval left bubble - mirrors SF `bubble.left` (not the square message icon). */
  val Bubble: ImageVector by lazy {
    strokeIcon(
      "AppwinBubble",
      "M12 20.25C16.9706 20.25 21 16.5563 21 12C21 7.44365 16.9706 3.75 12 3.75" +
        "C7.02944 3.75 3 7.44365 3 12C3 14.1036 3.85891 16.0234 5.2728 17.4806" +
        "C5.70538 17.9265 6.01357 18.5192 5.85933 19.121C5.68829 19.7883 5.368 20.3959" +
        " 4.93579 20.906C5.0918 20.9339 5.25 20.9558 5.40967 20.9713C5.60376 20.9903" +
        " 5.80078 21 6 21C7.28201 21 8.47016 20.5979 9.44517 19.9129C10.2551 20.1323" +
        " 11.1125 20.25 12 20.25Z",
    )
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
