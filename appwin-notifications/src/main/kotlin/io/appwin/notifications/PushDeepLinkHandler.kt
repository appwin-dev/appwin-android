package io.appwin.notifications

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Opens campaign deeplinks from push notification taps (foreground and background). */
internal object PushDeepLinkHandler {
  internal const val EXTRA_DEEPLINK = "deeplink"
  internal const val EXTRA_DELIVERY_ID = "deliveryId"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  fun dispatch(activity: Activity, intent: Intent?) {
    val deliveryId = extractDeliveryId(intent)
    val deeplink = extractDeeplink(intent)
    if (deliveryId.isNullOrBlank() && deeplink.isNullOrBlank()) return

    clearDeeplink(intent)
    clearDeliveryId(intent)

    scope.launch {
      if (!deliveryId.isNullOrBlank()) {
        runCatching { AppwinNotifications.track(deliveryId, TrackEvent.CLICKED) }
      }
      if (!deeplink.isNullOrBlank()) {
        openUrl(activity, deeplink)
      }
    }
  }

  fun openUrl(context: Context, url: String) {
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }
  }

  private fun extractDeeplink(intent: Intent?): String? {
    if (intent == null) return null
    return intent.getStringExtra(EXTRA_DEEPLINK)?.takeIf { it.isNotBlank() }
      ?: intent.extras?.getString(EXTRA_DEEPLINK)?.takeIf { it.isNotBlank() }
  }

  private fun extractDeliveryId(intent: Intent?): String? {
    if (intent == null) return null
    intent.getStringExtra(EXTRA_DELIVERY_ID)?.takeIf { it.isNotBlank() }?.let { return it }
    val extras = intent.extras ?: return null
    extras.getString(EXTRA_DELIVERY_ID)?.takeIf { it.isNotBlank() }?.let { return it }
    // FCM data fields are copied into launcher intent extras on notification tap.
    for (key in extras.keySet()) {
      if (key.equals(EXTRA_DELIVERY_ID, ignoreCase = true)) {
        extras.getString(key)?.takeIf { it.isNotBlank() }?.let { return it }
      }
    }
    return null
  }

  private fun clearDeliveryId(intent: Intent?) {
    intent ?: return
    intent.removeExtra(EXTRA_DELIVERY_ID)
    intent.extras?.remove(EXTRA_DELIVERY_ID)
  }

  private fun clearDeeplink(intent: Intent?) {
    intent ?: return
    intent.removeExtra(EXTRA_DEEPLINK)
    intent.extras?.remove(EXTRA_DEEPLINK)
  }
}
