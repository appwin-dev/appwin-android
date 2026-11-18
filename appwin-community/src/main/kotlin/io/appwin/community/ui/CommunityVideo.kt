package io.appwin.community.ui

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Feed / detail video cell: poster (or dark placeholder) + play overlay; tap
 * opens a full-screen [VideoView] with [MediaController]. Public URLs only -
 * no re-sign (unlike Support attachments).
 */
@Composable
internal fun CommunityVideoCell(
  url: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
  closeLabel: String,
) {
  var poster by remember(url) { mutableStateOf<Bitmap?>(null) }
  var showPlayer by remember { mutableStateOf(false) }

  LaunchedEffect(url) {
    poster = withContext(Dispatchers.IO) { loadPosterFrame(url) }
  }

  Box(
    modifier = modifier
      .background(Color(0xFF0F172A))
      .clickable { showPlayer = true },
    contentAlignment = Alignment.Center,
  ) {
    val frame = poster
    if (frame != null) {
      Image(
        bitmap = frame.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize(),
      )
    }

    // Dim overlay so the play glyph stays readable on bright posters.
    Spacer(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.12f)),
    )

    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.35f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Default.PlayArrow,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(28.dp),
      )
    }
  }

  if (showPlayer) {
    CommunityFullScreenVideoPlayer(
      url = url,
      closeLabel = closeLabel,
      onDismiss = { showPlayer = false },
    )
  }
}

/** Compact pending-attachment thumb in the composer (no full-screen player). */
@Composable
internal fun CommunityVideoThumb(
  url: String,
  modifier: Modifier = Modifier,
) {
  var poster by remember(url) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(url) {
    poster = withContext(Dispatchers.IO) { loadPosterFrame(url) }
  }

  Box(
    modifier = modifier.background(Color(0xFF0F172A)),
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
    }
    Icon(
      Icons.Default.PlayArrow,
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(28.dp),
    )
  }
}

@Composable
private fun CommunityFullScreenVideoPlayer(
  url: String,
  closeLabel: String,
  onDismiss: () -> Unit,
) {
  var buffering by remember { mutableStateOf(true) }

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
            buffering = true
            video.setVideoURI(Uri.parse(url))
          }
        },
        modifier = Modifier.fillMaxSize(),
      )

      if (buffering) {
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

/** First frame of a remote (or local) video URL. Best-effort; null on failure. */
internal fun loadPosterFrame(url: String): Bitmap? {
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
