package io.appwin.notifications

import io.appwin.core.AppwinCore
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.reportUnavailable
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Appwin Notifications SDK for Android.
 *
 * Three jobs: register the device token, emit the events that trigger
 * automations, and fetch pending in-app messages.
 *
 * Rendering in-app messages is **not** here: this module returns the data and
 * the host app displays it. The iOS SDK draws those screens itself; shipping
 * them in Compose is separate work.
 *
 * [AppwinCore.configure] must have been called first.
 */
public object AppwinNotifications {
  public const val VERSION: String = "0.1.0-dev"

  /**
   * Prepares Notifications for this app, and says whether it may be used.
   *
   * Call it after [AppwinCore.configure] and **before** asking the system for push permission, then gate your
   * own UI on the result: the SDK cannot hide your button or your tab, it does
   * not own your navigation.
   *
   * ```kotlin
   * if (AppwinCore.availability(AppwinProduct.NOTIFICATIONS).isReady) {
   *   requestPushPermission()
   * }
   * ```
   *
   * Idempotent, and cheap after the first call: the three products share one
   * server round trip and its cached verdict.
   */
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.NOTIFICATIONS)
    isReady = result.isReady
    if (!result.isReady) reportUnavailable(AppwinProduct.NOTIFICATIONS, result)
    return result
  }

  /** Whether [initialize] has returned [AppwinInitResult.Ready]. */
  @JvmStatic
  public var isReady: Boolean = false
    private set

  private const val BASE = "/api/sdk/notifications/v1"

  private val client: ApiClient
    get() = AppwinCore.client ?: throw AppwinApiException.NotConfigured()

  /**
   * Registers this device's FCM token.
   *
   * Call again on every token rotation (`onNewToken`). Without it the device
   * becomes unreachable silently: the server keeps accepting sends, they simply
   * stop arriving.
   *
   * Set [pushOptIn] to `false` rather than stopping registration: that
   * distinguishes "declined" from "never asked".
   */
  @JvmStatic
  @JvmOverloads
  public suspend fun registerPushToken(
    token: String,
    platform: String = "android",
    pushOptIn: Boolean = true,
  ) {
    require(token.isNotBlank()) { "token must not be blank" }

    client.requestVoid(
      path = "$BASE/push-token",
      method = HttpMethod.POST,
      body = ApiClient.json.encodeToString(
        PushTokenBody.serializer(),
        PushTokenBody(token = token, platform = platform, pushOptIn = pushOptIn),
      ),
    )
  }

  /**
   * Emits an SDK event, which can trigger an automation.
   *
   * [eventName] only applies to [AutomationEvent.CUSTOM_EVENT]: it is what
   * names the event in the studio.
   */
  @JvmStatic
  @JvmOverloads
  public suspend fun trackEvent(event: AutomationEvent, eventName: String? = null) {
    client.requestVoid(
      path = "$BASE/events",
      method = HttpMethod.POST,
      body = ApiClient.json.encodeToString(
        AutomationEventBody.serializer(),
        AutomationEventBody(event = event.wireValue, eventName = eventName),
      ),
    )
  }

  /** In-app messages pending for this device. */
  @JvmStatic
  public suspend fun fetchPendingMessages(): List<InAppMessage> = client.request(
    path = "$BASE/messages",
    method = HttpMethod.GET,
    deserializer = ListSerializer(InAppMessage.serializer()),
  )

  /** Reports what the user did with a message: seen, clicked, dismissed. */
  @JvmStatic
  public suspend fun track(deliveryId: String, event: TrackEvent) {
    client.requestVoid(
      path = "$BASE/track",
      method = HttpMethod.POST,
      body = ApiClient.json.encodeToString(
        TrackBody.serializer(),
        TrackBody(deliveryId = deliveryId, event = event.wireValue),
      ),
    )
  }

  /**
   * App-open shortcut: emits `app_open`, then returns the pending messages.
   *
   * The event is sent **before** the read, so an automation hooked on app open
   * can produce a message that the same call brings back.
   */
  @JvmStatic
  public suspend fun syncOnAppOpen(): List<InAppMessage> {
    trackEvent(AutomationEvent.APP_OPEN)
    return fetchPendingMessages()
  }
}

/** Events that can trigger an automation. */
public enum class AutomationEvent(public val wireValue: String) {
  APP_OPEN("app_open"),
  APP_BACKGROUND("app_background"),
  PURCHASE("purchase"),
  CUSTOM_EVENT("custom_event"),
  PUSH_OPT_IN("push_opt_in"),
  SESSION_START("session_start"),
}

/** What a user did with an in-app message. */
public enum class TrackEvent(public val wireValue: String) {
  OPENED("opened"),
  CLICKED("clicked"),
  DISMISSED("dismissed"),
}

@Serializable
public data class InAppMessage(
  public val id: String,
  public val campaignId: String,
  public val deliveryId: String,
  public val channel: String,
  public val content: InAppContent,
  public val format: String,
)

@Serializable
public data class InAppContent(
  public val title: String? = null,
  public val body: String? = null,
  public val imageUrl: String? = null,
  public val deeplink: String? = null,
)

@Serializable
private data class PushTokenBody(
  val token: String,
  val platform: String,
  val pushOptIn: Boolean,
)

@Serializable
private data class AutomationEventBody(
  val event: String,
  val eventName: String? = null,
)

@Serializable
private data class TrackBody(
  val deliveryId: String,
  val event: String,
)
