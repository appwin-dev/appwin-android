package io.appwin.support.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The messenger's own glyphs, traced from the Figma frames.
 *
 * `Icons.Default` is Material's set: filled, heavier, and drawn on a different
 * grid. Next to the design's 1.5px outlines it reads as a foreign app pasted
 * into the sheet, so the three glyphs the messenger actually shows are carried
 * by the SDK instead.
 *
 * They are stroked rather than filled. [androidx.compose.material3.Icon] tints
 * the whole raster, so `tint` still recolours them like any Material icon.
 */
internal object SupportIcons {

  /** Paper plane with its fold - the "write to us" call to action. */
  val Send: ImageVector by lazy {
    strokeIcon("AppwinSend", translateX = -120f, translateY = -369f) {
      listOf(
        // The plane's outline.
        "M131.665 381.324L133.752 375.063C134.584 372.566 135 371.318 134.341 370.659C133.682 370" +
          " 132.434 370.416 129.937 371.248L123.716 373.322C122.328 373.784 121.634 374.016" +
          " 121.424 374.524C121.374 374.644 121.344 374.771 121.335 374.9C121.296 375.449" +
          " 121.813 375.966 122.848 377.001L123.036 377.189C123.206 377.359 123.291 377.444" +
          " 123.355 377.539C123.481 377.725 123.553 377.943 123.562 378.168C123.567 378.282" +
          " 123.549 378.401 123.513 378.638C123.383 379.508 123.318 379.942 123.395 380.277C123.548" +
          " 380.943 124.064 381.466 124.728 381.629C125.061 381.711 125.497 381.651 126.368" +
          " 381.533L126.415 381.527C126.661 381.493 126.784 381.477 126.902 381.484C127.116" +
          " 381.497 127.322 381.567 127.5 381.686C127.598 381.752 127.686 381.839 127.861" +
          " 382.014L128.029 382.182C129.036 383.189 129.539 383.692 130.073 383.666C130.22" +
          " 383.659 130.365 383.624 130.5 383.565C130.989 383.35 131.214 382.674 131.665 381.324Z",
        // The fold, which is what keeps it from reading as a plain triangle.
        "M124 381L134 371",
      )
    }
  }

  /** Tray - the way into the conversations already opened. */
  val Inbox: ImageVector by lazy {
    strokeIcon("AppwinInbox", translateX = -120f, translateY = -429f) {
      listOf(
        "M121.333 437C121.333 433.857 121.333 432.286 122.309 431.31C123.286 430.333 124.857" +
          " 430.333 128 430.333C131.142 430.333 132.714 430.333 133.69 431.31C134.666 432.286" +
          " 134.666 433.857 134.666 437C134.666 440.143 134.666 441.714 133.69 442.691C132.714" +
          " 443.667 131.142 443.667 128 443.667C124.857 443.667 123.286 443.667 122.309" +
          " 442.691C121.333 441.714 121.333 440.143 121.333 437Z",
        "M121.333 437.667H123.44C124.043 437.667 124.345 437.667 124.61 437.788C124.875 437.91" +
          " 125.072 438.14 125.465 438.598L125.868 439.069C126.261 439.527 126.457 439.756 126.722" +
          " 439.878C126.988 440 127.289 440 127.893 440H128.107C128.71 440 129.012 440 129.277" +
          " 439.878C129.542 439.756 129.738 439.527 130.131 439.069L130.535 438.598C130.928 438.14" +
          " 131.124 437.91 131.389 437.788C131.654 437.667 131.956 437.667 132.559 437.667H134.666",
      )
    }
  }

  /** Thin chevron closing the conversations row. */
  val ChevronRight: ImageVector by lazy {
    strokeIcon("AppwinChevronRight", translateX = -354f, translateY = -429f) {
      listOf("M360.5 433.5L363.5 437L360.5 440.5")
    }
  }

  /**
   * Builds a 16dp glyph from Figma's absolute path data.
   *
   * The coordinates are kept as exported and moved by a group instead of being
   * rebased by hand: re-exporting a frame then means pasting the `d` attribute
   * and the two offsets, with no arithmetic to get wrong.
   */
  private fun strokeIcon(
    name: String,
    translateX: Float,
    translateY: Float,
    paths: () -> List<String>,
  ): ImageVector =
    ImageVector.Builder(
      name = name,
      defaultWidth = 16.dp,
      defaultHeight = 16.dp,
      viewportWidth = 16f,
      viewportHeight = 16f,
    ).apply {
      addGroup(name = name, translationX = translateX, translationY = translateY)
      paths().forEach { data ->
        addPath(
          pathData = PathParser().parsePathString(data).toNodes(),
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 1.5f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        )
      }
      clearGroup()
    }.build()
}
