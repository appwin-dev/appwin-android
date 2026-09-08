package io.appwin.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.appwin.notifications.InAppMessage
import io.appwin.notifications.InAppMessageUiAction
import io.appwin.notifications.R

@Composable
internal fun InAppMessageScreen(
  message: InAppMessage,
  modifier: Modifier = Modifier,
  onAction: (InAppMessageUiAction) -> Unit,
  onDismiss: () -> Unit,
) {
  when (message.format) {
    "banner" -> BannerLayout(message, modifier, onAction, onDismiss)
    "fullscreen", "image_only" -> FullscreenLayout(message, modifier, onAction, onDismiss)
    else -> ModalLayout(message, modifier, onAction, onDismiss)
  }
}

@Composable
private fun ModalLayout(
  message: InAppMessage,
  modifier: Modifier,
  onAction: (InAppMessageUiAction) -> Unit,
  onDismiss: () -> Unit,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.45f))
      .clickable(onClick = onDismiss),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier = Modifier
        .padding(24.dp)
        .clickable(enabled = false) {},
      shape = RoundedCornerShape(16.dp),
    ) {
      MessageBody(message, onAction, onDismiss, Modifier.padding(20.dp))
    }
  }
}

@Composable
private fun BannerLayout(
  message: InAppMessage,
  modifier: Modifier,
  onAction: (InAppMessageUiAction) -> Unit,
  onDismiss: () -> Unit,
) {
  Box(modifier = modifier.fillMaxSize()) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
        .align(Alignment.TopCenter)
        .clickable { onAction(InAppMessageUiAction.PrimaryTap) },
      shape = RoundedCornerShape(12.dp),
    ) {
      MessageBody(message, onAction, onDismiss, Modifier.padding(16.dp), showClose = true)
    }
  }
}

@Composable
private fun FullscreenLayout(
  message: InAppMessage,
  modifier: Modifier,
  onAction: (InAppMessageUiAction) -> Unit,
  onDismiss: () -> Unit,
) {
  Box(modifier = modifier.fillMaxSize()) {
    if (message.format == "image_only" && message.content.imageUrl != null) {
      AsyncImage(
        model = message.content.imageUrl,
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .clickable { onAction(InAppMessageUiAction.PrimaryTap) },
        contentScale = ContentScale.Fit,
      )
    } else {
      MessageBody(message, onAction, onDismiss, Modifier.padding(24.dp))
    }
    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
      Text("✕")
    }
  }
}

@Composable
private fun MessageBody(
  message: InAppMessage,
  onAction: (InAppMessageUiAction) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  showClose: Boolean = false,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (showClose) {
      Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
          Text("✕")
        }
      }
    }
    message.content.imageUrl?.takeIf { message.format != "image_only" }?.let { url ->
      AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.Crop,
      )
    }
    message.content.title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
    message.content.body?.let {
      Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    message.content.buttons?.forEachIndexed { index, button ->
      Button(
        onClick = { onAction(InAppMessageUiAction.Button(index, button)) },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(button.label)
      }
    } ?: run {
      if (message.content.deeplink != null) {
        Button(
          onClick = { onAction(InAppMessageUiAction.PrimaryTap) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(R.string.appwin_notifications_cta_open))
        }
      }
    }
  }
}
