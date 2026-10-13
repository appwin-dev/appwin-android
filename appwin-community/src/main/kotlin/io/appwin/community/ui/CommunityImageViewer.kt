package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlin.math.abs

/**
 * Thumbnail that opens [CommunityImageViewer] on tap.
 * Child [clickable] consumes the event so a parent post [clickable] does not open.
 */
@Composable
internal fun CommunityTappableImage(
  url: String,
  contentDescription: String?,
  closeLabel: String,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
) {
  var showViewer by remember(url) { mutableStateOf(false) }

  AsyncImage(
    model = url,
    contentDescription = contentDescription,
    contentScale = contentScale,
    alignment = Alignment.Center,
    modifier = modifier.clickable { showViewer = true },
  )

  if (showViewer) {
    CommunityImageViewer(
      url = url,
      onDismiss = { showViewer = false },
      closeLabel = closeLabel,
    )
  }
}

/**
 * Full-screen black image viewer for public community URLs (no re-sign).
 * Pinch zoom, double-tap toggle, drag-down dismiss when scale == 1.
 */
@Composable
internal fun CommunityImageViewer(
  url: String,
  onDismiss: () -> Unit,
  closeLabel: String,
) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var dragY by remember { mutableFloatStateOf(0f) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = true,
      dismissOnClickOutside = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .graphicsLayer { alpha = 1f - (abs(dragY) / 400f).coerceIn(0f, 0.6f) },
    ) {
      AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .fillMaxSize()
          .padding(8.dp)
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y + dragY,
          )
          .pointerInput(scale) {
            detectTransformGestures { _, pan, zoom, _ ->
              val next = (scale * zoom).coerceIn(1f, 4f)
              scale = next
              offset = if (next > 1f) offset + pan else Offset.Zero
            }
          }
          .pointerInput(scale) {
            detectTapGestures(
              onDoubleTap = {
                if (scale > 1f) {
                  scale = 1f
                  offset = Offset.Zero
                } else {
                  scale = 2.5f
                }
              },
            )
          }
          .pointerInput(scale) {
            if (scale > 1f) return@pointerInput
            detectVerticalDragGestures(
              onVerticalDrag = { _, amount -> dragY += amount },
              onDragEnd = {
                if (abs(dragY) > 120f) onDismiss()
                else dragY = 0f
              },
              onDragCancel = { dragY = 0f },
            )
          },
      )

      IconButton(
        onClick = onDismiss,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(12.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.5f)),
      ) {
        Icon(Icons.Default.Close, contentDescription = closeLabel, tint = Color.White)
      }
    }
  }
}
