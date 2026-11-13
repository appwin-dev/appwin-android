package io.appwin.notifications

import android.content.Context
import io.appwin.core.AppwinInternalApi
import io.appwin.core.push.AppwinPushHandler
import io.appwin.core.push.AppwinPushPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notifications' share of a push. The deeplink is not opened here: Core opens
 * it for every product (an external URL right away, an `appwin://` route by
 * the product it names).
 */
@OptIn(AppwinInternalApi::class)
internal object NotificationsPushHandler : AppwinPushHandler {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onTap(context: Context, payload: AppwinPushPayload) {
    val deliveryId = payload.deliveryId ?: return
    scope.launch { runCatching { AppwinNotifications.track(deliveryId, TrackEvent.CLICKED) } }
  }

  override fun onMessage(context: Context, payload: AppwinPushPayload): Boolean {
    // Silent nudge, not a notification: an in-app message just became
    // pending server-side. Fetch and present in-app; never post to the
    // system tray.
    if (!payload.isSilent) return false
    scope.launch { NotificationsCoordinator.fetchAndPresent() }
    return true
  }
}
