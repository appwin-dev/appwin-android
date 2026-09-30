package io.appwin.support.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.appwin.support.domain.Attachment
import kotlin.math.abs

/**
 * Thread thumbnail for an image attachment.
 *
 * Tapping opens a full-screen viewer (iOS `ImageBubble` / `ImageViewerView`).
 * Without that path the photo stayed stuck at bubble size on Android / RN.
 */
@Composable
internal fun ImageAttachmentPreview(
  attachment: Attachment,
  resolveUrl: suspend (Attachment) -> String,
  modifier: Modifier = Modifier,
) {
  var showViewer by remember(attachment.id) { mutableStateOf(false) }
  val context = LocalContext.current

  AsyncImage(
    model = attachment.url,
    contentDescription = attachment.filename,
    contentScale = ContentScale.Crop,
    modifier = modifier
      .fillMaxWidth()
      .size(180.dp)
      .clip(RoundedCornerShape(SupportTokens.bubbleTailRadius * 4))
      .clickable { showViewer = true },
  )

  if (showViewer) {
    FullScreenImageViewer(
      attachment = attachment,
      resolveUrl = resolveUrl,
      fallbackUrl = attachment.url,
      onDismiss = { showViewer = false },
      closeLabel = SupportStrings(context).close,
    )
  }
}

@Composable
private fun FullScreenImageViewer(
  attachment: Attachment,
  resolveUrl: suspend (Attachment) -> String,
  fallbackUrl: String,
  onDismiss: () -> Unit,
  closeLabel: String,
) {
  var displayUrl by remember(attachment.id) { mutableStateOf(fallbackUrl) }
  var loading by remember { mutableStateOf(true) }
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var dragY by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(attachment.id) {
    loading = true
    displayUrl = resolveUrl(attachment).ifBlank { fallbackUrl }
    loading = false
  }

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
        model = displayUrl,
        contentDescription = attachment.filename,
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
        onSuccess = { loading = false },
        onError = { loading = false },
      )

      if (loading) {
        CircularProgressIndicator(
          color = Color.White,
          strokeWidth = 2.dp,
          modifier = Modifier
            .align(Alignment.Center)
            .size(40.dp),
        )
      }

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
