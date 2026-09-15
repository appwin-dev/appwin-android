package io.appwin.support.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.appwin.support.R
import io.appwin.support.domain.MessengerBannerSource
import io.appwin.support.domain.MessengerDesign
import io.appwin.support.domain.MessengerPresetBanner
import kotlin.math.hypot
import kotlin.math.min

/**
 * Home banner - Figma BannerForSupport (441:6615).
 *
 * 1:1 port of `messenger-banner.tsx` and of the iOS `MessengerBannerView`, from
 * the same rasterised assets: a studio switching preset must see the same
 * picture in the dashboard preview, on iOS and here.
 */
@Composable
internal fun MessengerBanner(
  design: MessengerDesign,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  // Match iOS HomeView: when the studio disables the banner, emit nothing.
  // An empty aspect-ratio Box still reserved height and pushed the greeting down.
  if (design.bannerSource == MessengerBannerSource.NONE) return

  Box(
    modifier
      .fillMaxWidth()
      .aspectRatio(SupportTokens.BANNER_ASPECT)
      .clip(RoundedCornerShape(SupportTokens.bannerRadius)),
  ) {
    when {
      design.bannerSource == MessengerBannerSource.CUSTOM && design.bannerUrl != null ->
        CustomBanner(design.bannerUrl!!, design.bannerFocusY, accent)

      else -> PresetBanner(design.presetBanner, accent)
    }
  }
}

@Composable
private fun PresetBanner(preset: MessengerPresetBanner, accent: Color) {
  when (preset) {
    MessengerPresetBanner.EMOJIS -> EmojisBanner(accent)
    MessengerPresetBanner.AMICALE -> AmicaleBanner(accent)
    MessengerPresetBanner.DISCRET -> InsetArtBanner(
      background = SupportTokens.surfaceMuted,
      art = R.drawable.banner_discret,
      inset = PercentInset(-15.9f, 16.67f, -89.23f, 16.67f),
    )
    MessengerPresetBanner.PHOTO -> PhotoBanner(accent)
    MessengerPresetBanner.ICON -> IconBanner(accent)
    MessengerPresetBanner.SERIOUS -> InsetArtBanner(
      background = SupportTokens.surfaceMuted,
      art = R.drawable.banner_serious,
      inset = PercentInset(-21.54f, 19.67f, -65.13f, 19.67f),
    )
  }
}

/* -------------------------------------------------------------------------- */
/* Inset helper (CSS top/right/bottom/left percentages)                       */
/* -------------------------------------------------------------------------- */

/** Percentages relative to the banner box, negative values bleeding outside. */
private data class PercentInset(
  val top: Float,
  val right: Float,
  val bottom: Float,
  val left: Float,
)

@Composable
private fun BoxWithConstraintsInset(
  inset: PercentInset,
  width: Dp,
  height: Dp,
  content: @Composable (Dp, Dp) -> Unit,
) {
  val left = width * (inset.left / 100f)
  val top = height * (inset.top / 100f)
  val w = width - left - width * (inset.right / 100f)
  val h = height - top - height * (inset.bottom / 100f)
  Box(Modifier.offset(x = left, y = top).size(w.coerceAtLeast(0.dp), h.coerceAtLeast(0.dp))) {
    content(w, h)
  }
}

/** Solid background plus a single piece of art placed by percentage inset. */
@Composable
private fun InsetArtBanner(background: Color, art: Int, inset: PercentInset) {
  BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
    BoxWithConstraintsInset(inset, maxWidth, maxHeight) { _, _ ->
      Image(
        painter = painterResource(art),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/* -------------------------------------------------------------------------- */
/* Emoji preset (441:7565)                                                    */
/* -------------------------------------------------------------------------- */

private enum class EmojiTileKind { A, B, WAVE, LAPTOP, LIFEBUOY, EMPTY }

private val EmojiTileKind.art: Int?
  get() = when (this) {
    EmojiTileKind.A -> R.drawable.banner_tile_a
    EmojiTileKind.B -> R.drawable.banner_tile_b
    EmojiTileKind.WAVE -> R.drawable.banner_emoji_wave
    EmojiTileKind.LAPTOP -> R.drawable.banner_emoji_laptop
    EmojiTileKind.LIFEBUOY -> R.drawable.banner_emoji_lifebuoy
    EmojiTileKind.EMPTY -> null
  }

private val EmojiTileKind.isEmoji: Boolean
  get() = this == EmojiTileKind.WAVE || this == EmojiTileKind.LAPTOP ||
    this == EmojiTileKind.LIFEBUOY

private data class EmojiTile(val inset: PercentInset, val kind: EmojiTileKind)

/** Same table as `EMOJI_TILES` in messenger-banner.tsx. */
private val EmojiTiles = listOf(
  EmojiTile(PercentInset(1.24f, 88.08f, 52.42f, -3.14f), EmojiTileKind.A),
  EmojiTile(PercentInset(-5.04f, 73.56f, 58.7f, 11.38f), EmojiTileKind.B),
  EmojiTile(PercentInset(-11.32f, 59.04f, 64.98f, 25.91f), EmojiTileKind.A),
  EmojiTile(PercentInset(-17.6f, 44.51f, 71.26f, 40.43f), EmojiTileKind.A),
  EmojiTile(PercentInset(-23.88f, 29.99f, 77.55f, 54.95f), EmojiTileKind.A),
  EmojiTile(PercentInset(-30.16f, 15.46f, 83.83f, 69.48f), EmojiTileKind.B),
  EmojiTile(PercentInset(-36.44f, 0.94f, 90.11f, 84f), EmojiTileKind.A),
  EmojiTile(PercentInset(45.93f, 86.04f, 7.73f, -1.1f), EmojiTileKind.A),
  EmojiTile(PercentInset(39.65f, 71.52f, 14.01f, 13.42f), EmojiTileKind.B),
  EmojiTile(PercentInset(33.37f, 56.99f, 20.29f, 27.95f), EmojiTileKind.WAVE),
  EmojiTile(PercentInset(27.09f, 42.47f, 26.58f, 42.47f), EmojiTileKind.LAPTOP),
  EmojiTile(PercentInset(20.81f, 27.95f, 32.86f, 56.99f), EmojiTileKind.LIFEBUOY),
  EmojiTile(PercentInset(14.53f, 13.42f, 39.14f, 71.52f), EmojiTileKind.B),
  EmojiTile(PercentInset(8.25f, -1.1f, 45.42f, 86.04f), EmojiTileKind.A),
  EmojiTile(PercentInset(90.62f, 84f, -36.96f, 0.94f), EmojiTileKind.A),
  EmojiTile(PercentInset(84.34f, 69.48f, -30.67f, 15.46f), EmojiTileKind.B),
  EmojiTile(PercentInset(78.06f, 54.95f, -24.39f, 29.99f), EmojiTileKind.A),
  EmojiTile(PercentInset(71.78f, 40.43f, -18.11f, 44.51f), EmojiTileKind.A),
  EmojiTile(PercentInset(65.5f, 25.91f, -11.83f, 59.04f), EmojiTileKind.EMPTY),
  EmojiTile(PercentInset(59.22f, 11.38f, -5.55f, 73.56f), EmojiTileKind.B),
  EmojiTile(PercentInset(52.94f, -3.14f, 0.73f, 88.08f), EmojiTileKind.A),
)

@Composable
private fun EmojisBanner(accent: Color) {
  BoxWithConstraints(Modifier.fillMaxSize().background(accent)) {
    val boxW = maxWidth
    val boxH = maxHeight
    EmojiTiles.forEach { tile ->
      BoxWithConstraintsInset(tile.inset, boxW, boxH) { cellW, cellH ->
        EmojiTileArt(tile.kind, cellW, cellH)
      }
    }
  }
}

/**
 * One rotated tile.
 *
 * The size comes from the CSS `hypot(87.6777cqw, 12.3223cqh)` pair that the
 * Figma export produces for a -8deg rotation: the tile is measured along its
 * own axes, not the cell's, so a plain fill would leave gaps at the corners.
 */
@Composable
private fun EmojiTileArt(kind: EmojiTileKind, cellW: Dp, cellH: Dp) {
  val w = hypot(0.876777f * cellW.value, 0.123223f * cellH.value).dp
  val h = hypot(0.123223f * cellW.value, 0.876777f * cellH.value).dp
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val art = kind.art
    if (art == null) {
      Box(
        Modifier
          .size(w, h)
          .rotate(-8f)
          .clip(RoundedCornerShape(20.dp))
          .background(Color.White.copy(alpha = 0.05f)),
      )
    } else {
      Image(
        painter = painterResource(art),
        contentDescription = null,
        contentScale = if (kind.isEmoji) ContentScale.Crop else ContentScale.Fit,
        modifier = Modifier.size(w, h).rotate(-8f),
      )
    }
  }
}

/* -------------------------------------------------------------------------- */
/* Remaining presets                                                          */
/* -------------------------------------------------------------------------- */

@Composable
private fun AmicaleBanner(accent: Color) {
  BoxWithConstraints(Modifier.fillMaxSize().background(accent)) {
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.2f)))
    BoxWithConstraintsInset(
      PercentInset(-14.87f, 22.83f, -15.38f, 22.83f),
      maxWidth,
      maxHeight,
    ) { _, _ ->
      Image(
        painter = painterResource(R.drawable.banner_amicale),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
private fun PhotoBanner(accent: Color) {
  BoxWithConstraints(Modifier.fillMaxSize().background(darken(accent, 0.45f))) {
    val top = maxHeight * (-260.51f / 100f)
    val bottom = maxHeight * (-101.03f / 100f)
    val imgH = maxHeight - top - bottom
    val imgW = imgH * (500f / 750f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Image(
        painter = painterResource(R.drawable.banner_photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.offset(y = top).size(imgW, imgH),
      )
    }
    Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.8f)))
  }
}

/**
 * Figma BannerForSupport style=icon (441:7957) / dashboard `IconBanner`.
 *
 * Drawn in Canvas so the 5% white rings stay crisp (PNG rings band; VectorDrawable
 * Image scaling softens the headset on some densities).
 */
@Composable
private fun IconBanner(accent: Color) {
  val headsetPath = remember {
    PathParser().parsePathString(IconHeadsetPath).toPath().apply {
      fillType = PathFillType.EvenOdd
    }
  }
  val micPath = remember {
    PathParser().parsePathString(IconMicPath).toPath()
  }

  Canvas(Modifier.fillMaxSize().background(accent)) {
    val w = size.width
    val h = size.height
    val ringWhite = Color.White.copy(alpha = 0.05f)

    // Same percent insets as messenger-banner.tsx / Figma 441:7957.
    listOf(
      floatArrayOf(-71.28f, 10f, -74.87f, 10f),
      floatArrayOf(-40.51f, 20f, -44.1f, 20f),
      floatArrayOf(-9.74f, 30f, -13.33f, 30f),
    ).forEach { inset ->
      val topPct = inset[0]
      val rightPct = inset[1]
      val bottomPct = inset[2]
      val leftPct = inset[3]
      val left = w * (leftPct / 100f)
      val top = h * (topPct / 100f)
      val right = w - w * (rightPct / 100f)
      val bottom = h - h * (bottomPct / 100f)
      val radius = min(right - left, bottom - top) / 2f
      drawCircle(
        color = ringWhite,
        radius = radius,
        center = Offset((left + right) / 2f, (top + bottom) / 2f),
      )
    }

    // Call-center square: top 24.62%, bottom 24.1%, centred.
    val sqTop = h * 0.2462f
    val side = h - sqTop - h * 0.241f
    val sqLeft = (w - side) / 2f

    // Headset vector inset 12.5% (viewBox 75×75).
    val hsPad = side * 0.125f
    val hsSize = side - hsPad * 2f
    withTransform({
      translate(sqLeft + hsPad, sqTop + hsPad)
      // Pivot at origin: default scale pivot is the canvas centre and shifts the glyph.
      scale(hsSize / 75f, hsSize / 75f, pivot = Offset.Zero)
    }) {
      drawPath(path = headsetPath, color = Color.White)
    }

    // Smile (icon-mic) insets on the same square.
    val micLeft = sqLeft + side * (39.64f / 100f)
    val micTop = sqTop + side * (64.64f / 100f)
    val micW = side * ((100f - 39.64f - 39.64f) / 100f)
    val micH = side * ((100f - 64.64f - 27.08f) / 100f)
    withTransform({
      translate(micLeft, micTop)
      scale(micW / IconMicViewW, micH / IconMicViewH, pivot = Offset.Zero)
    }) {
      drawPath(path = micPath, color = Color.White)
    }
  }
}

/** Dashboard / Figma `icon-headset.svg` path (viewBox 0 0 75 75). */
private const val IconHeadsetPath =
  "M8.33333 58.3333H14.5833C15.625 58.3333 16.6667 57.2917 16.6667 56.25V33.3333C16.6667 32.2917 15.625 31.25 14.5833 31.25H12.5V29.1667C12.5 15.4167 23.75 4.16667 37.5 4.16667C44.1667 4.16667 50.4167 6.66667 55.2083 11.4583C60 16.25 62.5 22.5 62.5 29.1667V31.25H60.4167C59.375 31.25 58.3333 32.2917 58.3333 33.3333V56.25C58.3333 57.2917 59.375 58.3333 60.4167 58.3333H62.2917C61.25 64.1667 56.25 68.75 50 68.75H45.625C45.2083 66.4583 43.125 64.5833 40.625 64.5833H34.375C31.4583 64.5833 29.1667 66.875 29.1667 69.7917C29.1667 72.7083 31.4583 75 34.375 75H40.625C42.2917 75 43.75 74.1667 44.7917 72.9167H50C58.5417 72.9167 65.4167 66.4583 66.4583 58.3333H66.6667C71.25 58.3333 75 54.5833 75 50V39.5833C75 35 71.25 31.25 66.6667 31.25V29.1667C66.6667 21.4583 63.5417 13.9583 58.125 8.54167C52.7083 3.125 45.2083 0 37.5 0C21.4583 0 8.33333 13.125 8.33333 29.1667V31.25C3.75 31.25 0 35 0 39.5833V50C0 54.5833 3.75 58.3333 8.33333 58.3333Z"

/** Dashboard / Figma `icon-mic.svg` path (viewBox 0 0 20.7268 8.28007). */
private const val IconMicPath =
  "M10.3634 8.28007C14.3217 8.28007 18.0717 6.40507 20.3634 3.28007C20.9884 2.44673 20.7801 0.988404 19.9467 0.363404C19.1134 -0.261596 17.6551 -0.0532655 17.0301 0.780068C15.5717 2.8634 13.0717 4.1134 10.3634 4.1134C7.65507 4.1134 5.15507 2.8634 3.69673 0.780068C3.07173 -0.0532655 1.82173 -0.261596 0.780068 0.363404C-0.0532655 0.988404 -0.261596 2.2384 0.363404 3.28007C2.65507 6.40507 6.40507 8.28007 10.3634 8.28007Z"

private const val IconMicViewW = 20.7268f
private const val IconMicViewH = 8.28007f

/**
 * Studio-uploaded banner.
 *
 * `bannerFocusY` only bites when the scaled image is taller than the box; a
 * wide image has no slack to pan and stays centred, as in the dashboard.
 */
@Composable
private fun CustomBanner(url: String, focusY: Float, accent: Color) {
  Box(Modifier.fillMaxSize().background(accent)) {
    AsyncImage(
      model = url,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = BiasAlignment(0f, (focusY.coerceIn(0f, 100f) / 50f) - 1f),
      modifier = Modifier.fillMaxSize(),
    )
  }
}
