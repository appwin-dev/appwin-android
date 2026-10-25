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
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.push.AppwinPush
import io.appwin.notifications.ui.InAppMessageScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Full-screen host for a single in-app message. */
public class InAppMessageActivity : ComponentActivity() {
  private var notificationsWereEnabledBeforeRequest = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val raw = intent.getStringExtra(EXTRA_MESSAGE)
    val message = raw?.let { Json.decodeFromString(InAppMessage.serializer(), it) }
    if (message == null) {
      finish()
      return
    }

    lifecycleScope.launch {
      runCatching { AppwinNotifications.track(message.deliveryId, TrackEvent.OPENED) }
    }

    setContent {
      InAppMessageScreen(
        message = message,
        modifier = Modifier.fillMaxSize(),
        onAction = { action -> handleAction(message, action) },
        onDismiss = { dismissMessage(message) },
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
        InAppMessageUiAction.Dismiss -> dismissMessage(message)
      }
    }
  }

  // The only place the SDK asks for the notification permission (API 33+): a
  // grant seen here is a real opt-in, which Core counts as `push_opt_in`.
  @OptIn(AppwinInternalApi::class)
  @Deprecated("Deprecated in Java")
  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray,
  ) {
    @Suppress("DEPRECATION")
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    val index = permissions.indexOf(android.Manifest.permission.POST_NOTIFICATIONS)
    if (index < 0) return
    val granted = grantResults.getOrNull(index) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (PushPermissionGrant.shouldSignalCore(notificationsWereEnabledBeforeRequest, granted)) {
      AppwinCore.notePushAuthorizationGranted()
    }
  }

  private fun areNotificationsEnabled(): Boolean =
    runCatching {
      (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
        .areNotificationsEnabled()
    }.getOrDefault(false)

  private fun dismissMessage(message: InAppMessage) {
    lifecycleScope.launch {
      runCatching { AppwinNotifications.track(message.deliveryId, TrackEvent.DISMISSED) }
      close()
    }
  }

  private fun performButtonAction(button: InAppButton) {
    when (button.action) {
      InAppButtonAction.DEEPLINK.wireValue -> button.url?.let { openUrl(it) }
      InAppButtonAction.OPT_IN_PUSH.wireValue -> {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
          // Read before asking: the answer decides whether a grant is news.
          notificationsWereEnabledBeforeRequest = areNotificationsEnabled()
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

  // `appwin://` routes stay inside the SDK (a message pointing at a Support
  // conversation); the system would find no app for them.
  @OptIn(AppwinInternalApi::class)
  private fun openUrl(url: String) {
    AppwinPush.openDeeplink(this, url)
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
