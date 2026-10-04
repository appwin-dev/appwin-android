package io.appwin.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.appwin.core.AppwinCore
import io.appwin.core.push.AppwinPush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Optional FCM entry point. The host app must add `firebase-messaging` and the
 * Google Services plugin; this service is merged from the SDK manifest.
 *
 * ```kotlin
 * // AndroidManifest.xml of the host app - only if you subclass:
 * <service android:name=".MyMessagingService"
 *          android:exported="false">
 *   <intent-filter>
 *     <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *   </intent-filter>
 * </service>
 * ```
 *
 * Or use this class as-is: token registration and [onNewToken] forwarding are
 * handled automatically.
 *
 * An app that already has its own `FirebaseMessagingService` removes this one
 * from its manifest and forwards to `AppwinPush` instead (see its docs).
 */
public open class AppwinFirebaseMessagingService : FirebaseMessagingService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    scope.launch {
      runCatching { AppwinCore.registerPushToken(token) }
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    // Called for data messages in any app state, and for notification
    // messages only in the foreground (the system displays them otherwise).
    if (AppwinPush.handleMessage(this, message.data)) return
    val notification = message.notification
    if (AppwinPush.handleForeground(this, message.data, notification?.title, notification?.body)) {
      return
    }
    PushNotificationPresenter.show(this, message)
  }
}
