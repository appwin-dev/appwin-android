package io.appwin.support.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.appwin.support.domain.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Instagram-DM-style preview width; height is 9:16 (iOS VideoBubble). */
private val PreviewWidth = 220.dp
private val PreviewHeight = PreviewWidth * 16f / 9f
private val PreviewCorner = 18.dp

/**
 * Video bubble matching iOS `VideoBubble`: poster + play, tap opens a full-screen
 * in-app player (not an inline `VideoView` with a floating `MediaController`).
 */
@Composable
internal fun VideoAttachmentPreview(
  attachment: Attachment,
  resolveUrl: suspend (Attachment) -> String,
  modifier: Modifier = Modifier,
) {
  var url by remember(attachment.id) { mutableStateOf(attachment.url) }
  var poster by remember(attachment.id) { mutableStateOf<Bitmap?>(null) }
  var showPlayer by remember { mutableStateOf(false) }
  val context = LocalContext.current

  LaunchedEffect(attachment.id) {
    url = resolveUrl(attachment)
    poster = withContext(Dispatchers.IO) { loadPosterFrame(url) }
  }

  Box(
    modifier = modifier
      .shadow(8.dp, RoundedCornerShape(PreviewCorner), clip = false)
      .size(PreviewWidth, PreviewHeight)
      .clip(RoundedCornerShape(PreviewCorner))
      .background(Color(0xFF0F172A))
      .clickable { showPlayer = true },
    contentAlignment = Alignment.Center,
  ) {
    val frame = poster
    if (frame != null) {
      Image(
        bitmap = frame.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      CircularProgressIndicator(
        color = Color.White,
        strokeWidth = 2.dp,
        modifier = Modifier.size(28.dp),
      )
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))

    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.35f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Default.PlayArrow,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(36.dp),
      )
    }

    Box(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(10.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(Color.Black.copy(alpha = 0.35f))
        .padding(6.dp),
    ) {
      Icon(
        SupportIcons.Video,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(14.dp),
      )
    }
  }

  if (showPlayer) {
    FullScreenVideoPlayer(
      attachment = attachment,
      resolveUrl = resolveUrl,
      fallbackUrl = url,
      onDismiss = { showPlayer = false },
      closeLabel = SupportStrings(context).close,
    )
  }
}

@Composable
private fun FullScreenVideoPlayer(
  attachment: Attachment,
  resolveUrl: suspend (Attachment) -> String,
  fallbackUrl: String,
  onDismiss: () -> Unit,
  closeLabel: String,
) {
  var playUrl by remember(attachment.id) { mutableStateOf(fallbackUrl) }
  var urlReady by remember { mutableStateOf(false) }
  // Stays true until MediaPlayer is prepared and start() has been called -
  // resolving the signed URL alone still leaves a black VideoView.
  var buffering by remember { mutableStateOf(true) }

  LaunchedEffect(attachment.id) {
    buffering = true
    urlReady = false
    playUrl = resolveUrl(attachment).ifBlank { fallbackUrl }
    urlReady = playUrl.isNotBlank()
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
        .background(Color.Black),
    ) {
      var videoView by remember { mutableStateOf<VideoView?>(null) }

      DisposableEffect(Unit) {
        onDispose {
          videoView?.stopPlayback()
          videoView = null
        }
      }

      if (urlReady) {
        AndroidView(
          factory = { ctx ->
            FrameLayout(ctx).apply {
              layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
              )
              val video = VideoView(ctx).also { videoView = it }
              val controller = MediaController(ctx)
              controller.setAnchorView(this)
              video.setMediaController(controller)
              video.setOnPreparedListener { mp ->
                mp.isLooping = false
                video.start()
                buffering = false
                controller.show(3_000)
              }
              video.setOnInfoListener { _, what, _ ->
                when (what) {
                  android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> buffering = true
                  android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> buffering = false
                }
                false
              }
              video.setOnErrorListener { _, _, _ ->
                buffering = false
                true
              }
              addView(
                video,
                FrameLayout.LayoutParams(
                  FrameLayout.LayoutParams.MATCH_PARENT,
                  FrameLayout.LayoutParams.MATCH_PARENT,
                  Gravity.CENTER,
                ),
              )
              video.tag = playUrl
              buffering = true
              video.setVideoURI(Uri.parse(playUrl))
            }
          },
          update = { layout ->
            val video = layout.getChildAt(0) as? VideoView ?: return@AndroidView
            videoView = video
            val current = video.tag as? String
            if (current != playUrl && playUrl.isNotBlank()) {
              video.tag = playUrl
              buffering = true
              video.setVideoURI(Uri.parse(playUrl))
            }
          },
          modifier = Modifier.fillMaxSize(),
        )
      }

      if (buffering || !urlReady) {
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

/** First frame of a remote video URL. Best-effort; null on failure. */
private fun loadPosterFrame(url: String): Bitmap? {
  if (url.isBlank()) return null
  val retriever = MediaMetadataRetriever()
  return try {
    retriever.setDataSource(url, HashMap())
    retriever.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
  } catch (_: Exception) {
    null
  } finally {
    runCatching { retriever.release() }
  }
}
