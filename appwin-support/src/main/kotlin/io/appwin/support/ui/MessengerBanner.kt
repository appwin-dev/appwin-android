package io.appwin.support.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
  Box(
    modifier
      .fillMaxWidth()
      .aspectRatio(SupportTokens.BANNER_ASPECT)
      .clip(RoundedCornerShape(SupportTokens.bannerRadius)),
  ) {
    when {
      design.bannerSource == MessengerBannerSource.NONE -> Unit

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

@Composable
private fun IconBanner(accent: Color) {
  BoxWithConstraints(Modifier.fillMaxSize().background(accent)) {
    val boxW = maxWidth
    val boxH = maxHeight
    listOf(
      PercentInset(-71.28f, 10f, -74.87f, 10f) to R.drawable.banner_icon_ring_outer,
      PercentInset(-40.51f, 20f, -44.1f, 20f) to R.drawable.banner_icon_ring_mid,
      PercentInset(-9.74f, 30f, -13.33f, 30f) to R.drawable.banner_icon_ring_inner,
    ).forEach { (inset, art) ->
      BoxWithConstraintsInset(inset, boxW, boxH) { _, _ ->
        Image(
          painter = painterResource(art),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }

    // Headset and mic live in a centred square, not in the banner box: the
    // Figma insets below are relative to that square.
    val side = boxH - boxH * 0.2462f - boxH * 0.241f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Box(Modifier.offset(y = boxH * 0.2462f).size(side)) {
        BoxWithConstraintsInset(PercentInset(12.5f, 12.5f, 12.5f, 12.5f), side, side) { _, _ ->
          Image(
            painter = painterResource(R.drawable.banner_icon_headset),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
          )
        }
        BoxWithConstraintsInset(
          PercentInset(64.64f, 39.64f, 27.08f, 39.64f),
          side,
          side,
        ) { _, _ ->
          Image(
            painter = painterResource(R.drawable.banner_icon_mic),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}

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
