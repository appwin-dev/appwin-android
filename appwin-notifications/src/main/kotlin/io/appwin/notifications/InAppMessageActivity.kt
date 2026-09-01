package io.appwin.notifications

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import io.appwin.notifications.ui.InAppMessageScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Full-screen host for a single in-app message. */
public class InAppMessageActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val raw = intent.getStringExtra(EXTRA_MESSAGE)
    val message = raw?.let { Json.decodeFromString(InAppMessage.serializer(), it) }
    if (message == null) {
      finish()
      return
    }

    setContent {
      InAppMessageScreen(
        message = message,
        modifier = Modifier.fillMaxSize(),
        onAction = { action -> handleAction(message, action) },
        onDismiss = { close() },
      )
    }
  }

  private fun handleAction(message: InAppMessage, action: InAppMessageUiAction) {
    lifecycleScope.launch {
      when (action) {
        InAppMessageUiAction.PrimaryTap -> {
          runCatching { AppwinNotifications.track(message.deliveryId, TrackEvent.CLICKED) }
          message.content.deeplink?.let { openUrl(it) }
        }
        is InAppMessageUiAction.Button -> {
          runCatching {
            AppwinNotifications.track(message.deliveryId, TrackEvent.CLICKED, action.index)
          }
          performButtonAction(action.button)
        }
        InAppMessageUiAction.Dismiss -> {
          runCatching { AppwinNotifications.track(message.deliveryId, TrackEvent.DISMISSED) }
          close()
        }
      }
    }
  }

  private fun performButtonAction(button: InAppButton) {
    when (button.action) {
      InAppButtonAction.DEEPLINK.wireValue -> button.url?.let { openUrl(it) }
      InAppButtonAction.OPT_IN_PUSH.wireValue -> {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
          requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }
      }
      InAppButtonAction.OPEN_SETTINGS.wireValue -> {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", packageName, null)
        })
      }
    }
    if (button.action == InAppButtonAction.DISMISS.wireValue) close()
  }

  private fun openUrl(url: String) {
    runCatching {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
  }

  private fun close() {
    InAppMessagePresenter.onMessageClosed()
    finish()
  }

  public companion object {
    public const val EXTRA_MESSAGE: String = "appwin.notifications.message"

    internal fun encode(message: InAppMessage): String =
      Json.encodeToString(InAppMessage.serializer(), message)
  }
}

internal sealed interface InAppMessageUiAction {
  data object PrimaryTap : InAppMessageUiAction
  data class Button(val index: Int, val button: InAppButton) : InAppMessageUiAction
  data object Dismiss : InAppMessageUiAction
}
