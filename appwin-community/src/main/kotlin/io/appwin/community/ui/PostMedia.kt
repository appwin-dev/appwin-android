package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.isVideo

// Photos and videos of a post (Figma feed 5:1837).

@Composable
internal fun PostMediaGrid(
  media: List<CommunityMedia>,
  clipShape: androidx.compose.ui.graphics.Shape,
  closeLabel: String,
  modifier: Modifier = Modifier,
) {
  when {
    media.isEmpty() -> Unit
    media.size == 1 -> Box(modifier) {
      // Cap portrait height (4:5): taller photos crop top/bottom instead of
      // stretching the feed cell to the full image height.
      SinglePostMedia(
        item = media[0],
        clipShape = clipShape,
        closeLabel = closeLabel,
      )
    }
    // Figma: two photos side by side, tall portrait cells.
    media.size == 2 -> Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      media.forEach { item ->
        MediaCell(
          item = item,
          clipShape = clipShape,
          closeLabel = closeLabel,
          modifier = Modifier.weight(1f).aspectRatio(170f / 300f),
        )
      }
    }
    else -> {
      val cells = media.take(4)
      Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(2).forEach { row ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            row.forEach { item ->
              val absoluteIndex = cells.indexOf(item)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .aspectRatio(1f)
                  .clip(clipShape)
                  .background(CommunityColors.surfaceMuted),
              ) {
                if (item.isVideo) {
                  CommunityVideoCell(
                    url = item.url,
                    contentDescription = item.alt,
                    modifier = Modifier.fillMaxSize(),
                    closeLabel = closeLabel,
                  )
                } else {
                  CommunityTappableImage(
                    url = item.url,
                    contentDescription = item.alt,
                    closeLabel = closeLabel,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                  )
                }
                if (absoluteIndex == 3 && media.size > 4) {
                  Text(
                    text = "+${media.size - 4}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .padding(6.dp)
                      .clip(RoundedCornerShape(50))
                      .background(Color.Black.copy(alpha = 0.55f))
                      .padding(horizontal = 6.dp, vertical = 4.dp),
                  )
                }
              }
            }
            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

/**
 * Single media cell. Mirrors iOS `PostMediaGrid` case 1:
 * - frame aspect = max(natural, 4:5) with natural from metadata, else 4:3
 * - image `scaledToFill` + clip (Compose [ContentScale.Crop] + [Alignment.Center])
 *
 * Do not resize the frame from Coil intrinsic size after load: iOS keeps the
 * metadata/4:3 frame, and swapping aspect mid-load changed the crop vs iOS.
 */
@Composable
private fun SinglePostMedia(
  item: CommunityMedia,
  clipShape: androidx.compose.ui.graphics.Shape,
  closeLabel: String,
) {
  val natural =
    if (item.width != null && item.height != null && item.height > 0) {
      item.width.toFloat() / item.height.toFloat()
    } else {
      4f / 3f
    }
  val ratio = maxOf(natural, MinSingleMediaAspect)
  val frame = Modifier
    .fillMaxWidth()
    .aspectRatio(ratio)
    .clip(clipShape)
    .background(CommunityColors.surfaceMuted)

  if (item.isVideo) {
    CommunityVideoCell(
      url = item.url,
      contentDescription = item.alt,
      modifier = frame,
      contentScale = ContentScale.Crop,
      closeLabel = closeLabel,
    )
    return
  }

  // Same stacking as iOS: fixed aspect frame, then fill+clip inside.
  Box(modifier = frame) {
    CommunityTappableImage(
      url = item.url,
      contentDescription = item.alt,
      closeLabel = closeLabel,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
    )
  }
}

@Composable
private fun MediaCell(
  item: CommunityMedia,
  clipShape: androidx.compose.ui.graphics.Shape,
  closeLabel: String,
  modifier: Modifier,
) {
  Box(modifier = modifier.clip(clipShape).background(CommunityColors.surfaceMuted)) {
    if (item.isVideo) {
      CommunityVideoCell(
        url = item.url,
        contentDescription = item.alt,
        modifier = Modifier.fillMaxSize(),
        closeLabel = closeLabel,
      )
    } else {
      CommunityTappableImage(
        url = item.url,
        contentDescription = item.alt,
        closeLabel = closeLabel,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    }
  }
}

/** Tallest single-media frame in the feed (width / height). Same as iOS. */
private const val MinSingleMediaAspect = 4f / 5f
