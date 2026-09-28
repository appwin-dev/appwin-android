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
   * Lucide `Package` (dashboard `ProjectAvatar` type=`other`), 24dp grid.
   *
   * Used as the no-logo agent/project disc, same silhouette as iOS
   * `shippingbox.fill`.
   */
  val Package: ImageVector by lazy {
    ImageVector.Builder(
      name = "AppwinPackage",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      listOf(
        "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
        "M12 22V12",
        "m3.3 7 7.703 4.734a2 2 0 0 0 1.994 0L20.7 7",
        "m7.5 4.27 9 5.15",
      ).forEach { data ->
        addPath(
          pathData = PathParser().parsePathString(data).toNodes(),
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 2f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        )
      }
    }.build()
  }

  /**
   * Solar Gallery Linear (Figma composer) - 16dp viewBox.
   *
   * Replaces the earlier Lucide Image: the messenger toolbar must match the
   * Solar set used on the dashboard and iOS.
   */
  val Gallery: ImageVector by lazy {
    solar16("AppwinGallery") {
      strokes(
        "M1.33333 8C1.33333 4.8573 1.33333 3.28595 2.30964 2.30964C3.28595 1.33333 " +
          "4.8573 1.33333 8 1.33333C11.1427 1.33333 12.714 1.33333 13.6904 2.30964C14.6667 " +
          "3.28595 14.6667 4.8573 14.6667 8C14.6667 11.1427 14.6667 12.714 13.6904 " +
          "13.6904C12.714 14.6667 11.1427 14.6667 8 14.6667C4.8573 14.6667 3.28595 " +
          "14.6667 2.30964 13.6904C1.33333 12.714 1.33333 11.1427 1.33333 8Z",
        "M1.33333 8.33342L2.50106 7.31167C3.10857 6.7801 4.02418 6.81059 4.59499 " +
          "7.3814L7.45481 10.2412C7.91296 10.6994 8.63416 10.7618 9.16427 10.3893L9.36307 " +
          "10.2496C10.1259 9.71347 11.158 9.77558 11.851 10.3993L14 12.3334",
      )
      circleStroke(cx = 10.6667f, cy = 5.33333f, r = 1.33333f)
    }
  }

  /** Solar Video Library Linear (Figma composer). */
  val Video: ImageVector by lazy {
    solar16("AppwinVideoLibrary") {
      strokes(
        "M1.93715 9.19532C1.65595 7.08629 1.51535 6.03177 2.11293 5.34922C2.71052 " +
          "4.66667 3.77437 4.66667 5.90207 4.66667H10.0979C12.2256 4.66667 13.2895 " +
          "4.66667 13.8871 5.34922C14.4847 6.03177 14.3441 7.08629 14.0628 9.19532L13.7962 " +
          "11.1953C13.5757 12.8492 13.4654 13.6762 12.8998 14.1714C12.3341 14.6667 " +
          "11.4998 14.6667 9.83127 14.6667H6.16873C4.50017 14.6667 3.6659 14.6667 " +
          "3.10025 14.1714C2.5346 13.6762 2.42434 12.8492 2.20382 11.1953L1.93715 9.19532Z",
        "M13.0413 4.66667C13.1937 3.79682 12.5244 3 11.6413 3H4.35873C3.47563 3 " +
          "2.80629 3.79682 2.95871 4.66667",
        "M11.6667 3C11.6856 2.82728 11.695 2.7409 11.6952 2.66956C11.6967 1.98715 " +
          "11.1827 1.41376 10.5041 1.34095C10.4332 1.33333 10.3463 1.33333 10.1726 " +
          "1.33333H5.82737C5.65361 1.33333 5.56672 1.33333 5.49579 1.34095C4.81727 " +
          "1.41376 4.30325 1.98715 4.30473 2.66956C4.30489 2.7409 4.31435 2.82727 " +
          "4.33326 3",
        "M9.25841 8.46199C9.97502 8.9735 10.3333 9.22925 10.3333 9.66669C10.3333 " +
          "10.1041 9.97502 10.3599 9.25841 10.8714C9.06058 11.0126 8.86437 11.1455 " +
          "8.68407 11.2563C8.5259 11.3535 8.34676 11.454 8.16129 11.5527C7.44635 " +
          "11.9331 7.08889 12.1233 6.76828 11.9128C6.44767 11.7022 6.41853 11.2613 " +
          "6.36025 10.3796C6.34377 10.1303 6.33333 9.88582 6.33333 9.66669C6.33333 " +
          "9.44755 6.34377 9.20311 6.36025 8.95376C6.41853 8.07206 6.44767 7.63121 " +
          "6.76828 7.42062C7.08889 7.21003 7.44635 7.40023 8.16129 7.78065C8.34676 " +
          "7.87933 8.5259 7.97987 8.68407 8.07705C8.86437 8.18784 9.06058 8.32078 " +
          "9.25841 8.46199Z",
      )
    }
  }

  /** Solar Paperclip Linear (Figma composer). */
  val Paperclip: ImageVector by lazy {
    solar16("AppwinPaperclip") {
      strokes(
        "M5.27834 11.8712L10.5389 6.8357C11.1705 6.23112 11.1705 5.25091 10.5389 " +
          "4.64634C9.90732 4.04176 8.8833 4.04176 8.25171 4.64634L3.02924 9.64537C1.82921 " +
          "10.7941 1.82921 12.6565 3.02924 13.8051C4.22928 14.9538 6.17491 14.9538 " +
          "7.37494 13.8051L12.6736 8.73314C14.4421 7.04033 14.4421 4.29575 12.6736 " +
          "2.60294C10.9052 0.910131 8.03793 0.910131 6.26946 2.60294L2 6.68974",
      )
    }
  }

  /** Solar Smile Circle Linear (Figma composer emoji). */
  val SmileCircle: ImageVector by lazy {
    solar16("AppwinSmileCircle") {
      circleStroke(cx = 8f, cy = 8f, r = 6.66667f)
      strokes("M6 10.6667C6.56692 11.0869 7.25638 11.3333 8 11.3333C8.74362 11.3333 9.43308 11.0869 10 10.6667")
      // Eyes: left ellipse + right filled pupil (Figma export).
      ovalFill(cx = 6f, cy = 7f, rx = 0.666667f, ry = 1f)
      ovalFill(cx = 10f, cy = 7f, rx = 0.666667f, ry = 1f)
    }
  }

  /** Solar Pen Linear - inbox “new conversation” CTA (iOS ConversationList). */
  val Pen: ImageVector by lazy {
    ImageVector.Builder(
      name = "AppwinPen",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      addPath(
        pathData = PathParser().parsePathString(
          "M14.3601 4.07866L15.2869 3.15178C16.8226 1.61607 19.3125 1.61607 " +
            "20.8482 3.15178C22.3839 4.68748 22.3839 7.17735 20.8482 8.71306L19.9213 " +
            "9.63993M14.3601 4.07866C14.3601 4.07866 14.4759 6.04828 16.2138 7.78618C17.9517 " +
            "9.52407 19.9213 9.63993 19.9213 9.63993M14.3601 4.07866L5.83882 12.5999C5.26166 " +
            "13.1771 4.97308 13.4656 4.7249 13.7838C4.43213 14.1592 4.18114 14.5653 " +
            "3.97634 14.995C3.80273 15.3593 3.67368 15.7465 3.41556 16.5208L2.32181 " +
            "19.8021M19.9213 9.63993L11.4001 18.1612C10.8229 18.7383 10.5344 19.0269 " +
            "10.2162 19.2751C9.84082 19.5679 9.43469 19.8189 9.00498 20.0237C8.6407 " +
            "20.1973 8.25352 20.3263 7.47918 20.5844L4.19792 21.6782M4.19792 21.6782L3.39584 " +
            "21.9456C3.01478 22.0726 2.59466 21.9734 2.31063 21.6894C2.0266 21.4053 " +
            "1.92743 20.9852 2.05445 20.6042L2.32181 19.8021M4.19792 21.6782L2.32181 19.8021",
        ).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      )
    }.build()
  }

  /** Lucide `FileText` - non-image attachment card. */
  val Document: ImageVector by lazy {
    stroke24("AppwinDocument") {
      listOf(
        "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z",
        "M14 2v4a2 2 0 0 0 2 2h4",
        "M10 9H8",
        "M16 13H8",
        "M16 17H8",
      )
    }
  }

  /** Lucide `ExternalLink` - open attachment affordance. */
  val ExternalLink: ImageVector by lazy {
    stroke24("AppwinExternalLink") {
      listOf(
        "M15 3h6v6",
        "M10 14 21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
      )
    }
  }

  private fun stroke24(name: String, paths: () -> List<String>): ImageVector =
    ImageVector.Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      paths().forEach { data ->
        addPath(
          pathData = PathParser().parsePathString(data).toNodes(),
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 2f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        )
      }
    }.build()

  /**
   * Solar Linear icons as exported from Figma (16×16, 1.5 stroke).
   *
   * Kept separate from [strokeIcon]: those still use absolute Figma artboard
   * coordinates + a translate group. Composer icons ship already normalised.
   */
  private fun solar16(name: String, block: Solar16Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
      name = name,
      defaultWidth = 16.dp,
      defaultHeight = 16.dp,
      viewportWidth = 16f,
      viewportHeight = 16f,
    ).apply {
      Solar16Builder(this).block()
    }.build()

  private class Solar16Builder(private val builder: ImageVector.Builder) {
    fun strokes(vararg paths: String) {
      paths.forEach { data ->
        builder.addPath(
          pathData = PathParser().parsePathString(data).toNodes(),
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 1.5f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        )
      }
    }

    fun circleStroke(cx: Float, cy: Float, r: Float) {
      // Two semicircle arcs - PathParser has no dedicated circle primitive.
      strokes(
        "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy",
      )
    }

    fun ovalFill(cx: Float, cy: Float, rx: Float, ry: Float) {
      builder.addPath(
        pathData = PathParser().parsePathString(
          "M${cx - rx} $cy A$rx $ry 0 1 0 ${cx + rx} $cy A$rx $ry 0 1 0 ${cx - rx} $cy",
        ).toNodes(),
        fill = SolidColor(Color.Black),
      )
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
