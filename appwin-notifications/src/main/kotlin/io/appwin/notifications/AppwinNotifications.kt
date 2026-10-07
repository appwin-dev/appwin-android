package io.appwin.notifications

import android.content.Context
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.reportUnavailable
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import io.appwin.core.push.AppwinPush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * Appwin Notifications SDK for Android.
 *
 * Intercom model: after `configure` + `initialize`, call [start] once and the SDK
 * owns lifecycle events, in-app presentation, and the current FCM token when the
 * host ships `firebase-messaging` + `google-services.json`. Token rotations still
 * go through [AppwinFirebaseMessagingService.onNewToken].
 *
 * [AppwinCore.configure] must have been called first.
 */
public object AppwinNotifications {
  public const val VERSION: String = "0.2.0-dev"

  @OptIn(AppwinInternalApi::class)
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.NOTIFICATIONS)
    isReady = result.isReady
    if (!result.isReady) {
      reportUnavailable(AppwinProduct.NOTIFICATIONS, result)
      AppwinPush.unregister(AppwinProduct.NOTIFICATIONS.key)
    } else {
      AppwinCore.reportMissingPushToken(AppwinProduct.NOTIFICATIONS)
      AppwinPush.register(AppwinProduct.NOTIFICATIONS.key, NotificationsPushHandler)
    }
    return result
  }

  @JvmStatic
  public var isReady: Boolean = false
    private set

  private const val BASE = "/api/sdk/notifications/v1"

  private val client: ApiClient
    get() = AppwinCore.client ?: throw AppwinApiException.NotConfigured()

  /**
   * Starts lifecycle hooks and in-app presentation.
   *
   * ```kotlin
   * AppwinCore.configure(this, projectAppId = appId)
   * if (AppwinNotifications.initialize().isReady) {
   *   AppwinNotifications.start(this)
   * }
   * ```
   */
  @JvmStatic
  public fun start(context: Context) {
    NotificationsCoordinator.start(context.applicationContext as android.app.Application)
  }

  /**
   * Awaits the FCM token pull started by [start]. No-op when Firebase is
   * absent. Flutter should call this after [start] so likes are not tested
   * before a token exists.
   */
  @JvmStatic
  public suspend fun awaitPushToken(): Boolean =
    NotificationsCoordinator.registerCurrentFcmToken()

  @JvmStatic
  public fun stop() {
    NotificationsCoordinator.stop()
  }

  /** Call from `FirebaseMessagingService.onNewToken`. */
  @JvmStatic
  public suspend fun onNewToken(token: String, pushOptIn: Boolean = true) {
    AppwinCore.registerPushToken(token, pushOptIn = pushOptIn)
  }

  @JvmStatic
  @JvmOverloads
  public suspend fun trackEvent(
    event: AutomationEvent,
    eventName: String? = null,
    properties: Map<String, String>? = null,
  ) {
    client.requestVoid(
      path = "$BASE/events",
      method = HttpMethod.POST,
      body = ApiClient.json.encodeToString(
        AutomationEventBody.serializer(),
        AutomationEventBody(event = event.wireValue, eventName = eventName, properties = properties),
      ),
    )
  }

  @JvmStatic
  public suspend fun fetchPendingMessages(): List<InAppMessage> = client.request(
    path = "$BASE/messages",
    method = HttpMethod.GET,
    deserializer = ListSerializer(InAppMessage.serializer()),
  )

  @JvmStatic
  @JvmOverloads
  public suspend fun track(
    deliveryId: String,
    event: TrackEvent,
    buttonIndex: Int? = null,
  ) {
    client.requestVoid(
      path = "$BASE/track",
      method = HttpMethod.POST,
      body = ApiClient.json.encodeToString(
        TrackBody.serializer(),
        TrackBody(deliveryId = deliveryId, event = event.wireValue, buttonIndex = buttonIndex),
      ),
    )
  }

  @JvmStatic
  public suspend fun syncOnAppOpen(): List<InAppMessage> {
    trackEvent(AutomationEvent.APP_OPEN)
    return fetchPendingMessages()
  }

  @JvmStatic
  public suspend fun presentPendingMessages() {
    val messages = fetchPendingMessages()
    withContext(Dispatchers.Main) {
      InAppMessagePresenter.enqueue(messages)
    }
  }
}
