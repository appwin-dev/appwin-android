@file:OptIn(AppwinInternalApi::class)

package io.appwin.core

import android.app.Application
import android.content.Context
import io.appwin.core.analytics.AnalyticsConsent
import io.appwin.core.analytics.AnalyticsRuntime
import io.appwin.core.attribution.AdvertisingConsent
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.AvailabilityStore
import io.appwin.core.identity.DeviceInfo
import io.appwin.core.identity.IdentityStore
import io.appwin.core.identity.SecureStore
import io.appwin.core.inapp.AppwinInAppBanner
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.RealtimeHub
import io.appwin.core.push.AppwinPush
import io.appwin.core.push.PushTokenBody
import io.appwin.core.session.AuthSession
import io.appwin.core.session.IdentityManager
import io.appwin.core.storage.BucketUploader
import io.appwin.core.storage.MediaUploads
import io.appwin.core.storage.UploadedMediaRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Shared foundation for every Appwin product module on Android.
 *
 * Firebase-style: the host app calls [configure] once at launch, then the
 * product modules read [client] and [deviceId].
 *
 * The contract deliberately mirrors the iOS Core - same names, same
 * guarantees, same headers. A behavioural difference between the two is a bug,
 * not a platform variant.
 *
 * ```kotlin
 * class MyApp : Application() {
 *   override fun onCreate() {
 *     super.onCreate()
 *     AppwinCore.configure(this, projectAppId = "your-app-id")
 *   }
 * }
 * ```
 */
public object AppwinCore {
  /** Reported to the server for diagnostics. */
  public const val VERSION: String = "0.8.1"

  private const val DEVICE_ID_KEY = "appwin.core.deviceId"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var secureStore: SecureStore? = null
  private var availabilityStore: AvailabilityStore? = null
  private var realtimeHub: RealtimeHub? = null

  private val identity = IdentityManager(
    scope = scope,
    store = { secureStore },
    client = { client },
    baseUrl = { baseUrl },
    realtimeHub = { realtimeHub },
    openSession = ::openSession,
  )

  private val analytics = AnalyticsRuntime(
    scope = scope,
    client = { client },
    reauthorize = { runCatching { bootstrapSession() }.isSuccess },
  )

  /** Application context, retained for products that start after [configure]. */
  @Volatile
  private var appContext: Context? = null

  public var baseUrl: String = "https://api.appwin.io"
    private set

  /** Realtime service base URL. A **separate** service from the API. */
  public var realtimeBaseUrl: String = "https://ws.appwin.io"
    private set

  public var deviceInfo: DeviceInfo? = null
    private set

  /**
   * The application context [configure] was given.
   *
   * A product reacting to a realtime event has no screen to take a context
   * from, and waiting for one defeats the purpose: an in-app banner has to
   * fire on a screen that knows nothing about the product.
   */
  public var applicationContext: Context? = null
    private set

  /** Canonical HTTP client, `null` until [configure] has run. */
  public var client: ApiClient? = null
    private set

  /** Public project app id, shared across every product. */
  public val projectAppId: String?
    get() = IdentityStore.snapshot().projectAppId

  /** Stable device id, persisted across launches. */
  public val deviceId: String?
    get() = IdentityStore.snapshot().deviceId

  /**
   * User id passed to [identify], persisted across launches. `null` means
   * anonymous.
   */
  public val externalId: String?
    get() = IdentityStore.snapshot().externalId

  /**
   * Emits after [identify], [updateUser] and [logout] have completed, so the
   * product modules can reload what they show for the current user (Support's
   * customer, Community's profile).
   */
  @AppwinInternalApi
  public val identityChanges: SharedFlow<Unit>
    get() = identity.changes

  /** Whether [registerPushToken] has succeeded at least once this process. */
  public val hasRegisteredPushToken: Boolean
    get() = identity.hasRegisteredPushToken

  /**
   * Call once at launch, before using any product module. Idempotent.
   *
   * Synchronous: it prepares the device identity and network client right
   * away. The server session is opened lazily by the first call that needs
   * it.
   *
   * @param context only the application context is retained, never an
   *   activity - the SDK outlives them.
   * @param projectAppId public project id, found in the studio.
   * @param baseUrl API URL override, for development.
   * @param realtimeBaseUrl realtime service URL override.
   */
  @JvmStatic
  @JvmOverloads
  public fun configure(
    context: Context,
    projectAppId: String,
    baseUrl: String? = null,
    realtimeBaseUrl: String? = null,
  ) {
    require(projectAppId.isNotBlank()) { "projectAppId must not be blank" }

    baseUrl?.let { this.baseUrl = it.trimEnd('/') }
    realtimeBaseUrl?.let { this.realtimeBaseUrl = it.trimEnd('/') }

    val store = SecureStore(context)
    secureStore = store
    // Keyed by appId: two apps on the same device must not read each
    // other's verdict.
    availabilityStore = AvailabilityStore(context, projectAppId)

    val deviceId = store.get(DEVICE_ID_KEY) ?: UUID.randomUUID().toString().also {
      store.set(DEVICE_ID_KEY, it)
    }

    val storedExternalId = identity.storedExternalId(store)
    IdentityStore.mutate {
      it.copy(projectAppId = projectAppId, deviceId = deviceId, externalId = storedExternalId)
    }
    deviceInfo = DeviceInfo.current(context)

    client = ApiClient(baseUrl = this.baseUrl, headersProvider = ::canonicalHeaders)
    appContext = context.applicationContext

    applicationContext = context.applicationContext

    // The shared in-app surface, so a product that wants to show a banner has
    // one to show it on. Nothing is drawn until a product asks.
    (context.applicationContext as? Application)?.let(AppwinInAppBanner::install)
    // Before any product initializes: a cold start from a notification tap
    // carries the push on the launch intent, and it must be read now.
    (context.applicationContext as? Application)?.let(AppwinPush::install)

    // Nothing else: configure is 100% local (no network, no server row).
    // Every product - analytics included - starts through its own
    // `initialize()`, and the session is minted lazily by the first call
    // that needs a bearer (`availability` and the event pipeline both know
    // how to wait on `bootstrapSession`).
  }

  /**
   * Wired by `AppwinAnalytics.initialize()` once availability said yes -
   * never by [configure]: capture must not exist in an app that did not
   * adopt the product. Everything heavier than object creation runs in the
   * consumer's first turn, off the main thread. Idempotent.
   */
  @AppwinInternalApi
  public fun startAnalytics() {
    analyticsHost()?.let(analytics::ensurePipeline)
  }

  /**
   * Wired by `AppwinAttribution.initialize()` once availability said yes.
   * Starts the acquisition signals (Play Install Referrer, advertising
   * identity, ad-network adapters). Rides on the same event pipeline as
   * analytics, never on the Analytics product. Idempotent.
   */
  @AppwinInternalApi
  public fun startAttribution() {
    analyticsHost()?.let(analytics::startAttribution)
  }

  private fun analyticsHost(): AnalyticsRuntime.Host? {
    val context = appContext ?: return null
    val projectAppId = projectAppId ?: return null
    return AnalyticsRuntime.Host(context, projectAppId, deviceInfo?.appVersion)
  }

  /**
   * Whether this app may open [product], as the server sees it.
   *
   * Called by each product's `initialize()`; a host app has no reason to call
   * it directly. One shared request answers for all three products, and the
   * verdict is cached on disk so a launch without network falls back to the
   * last known answer rather than locking a paying studio out.
   */
  @JvmStatic
  public suspend fun availability(product: AppwinProduct): AppwinInitResult {
    val api = client ?: return AppwinInitResult.NotConfigured
    val store = availabilityStore ?: return AppwinInitResult.NotConfigured

    // Wait for the session first. `configure` returns before the bearer exists
    // - deliberately, so an offline app still starts fast - and this endpoint
    // is bearer-only. Called straight after `configure`, which is exactly what
    // the documented sequence tells a studio to do, the request would 401 and
    // report `Unknown`: "offline on a first launch" for an app that is online.
    //
    // Idempotent and shared between concurrent callers, so the three products
    // initialising at once still cost one round trip.
    runCatching { bootstrapSession() }

    return store.status(product, api)
  }

  /**
   * Opens (or rotates) the server session for the current identity and
   * returns the token. Concurrent callers share one network call.
   *
   * Always sends the persisted [externalId]: a re-bootstrap without it would
   * detach the session from the identified user server-side.
   */
  @AppwinInternalApi
  public suspend fun bootstrapSession(): String = identity.bootstrap()

  private suspend fun openSession(externalId: String?): String {
    val appId = projectAppId ?: throw AppwinApiException.NotConfigured()
    val deviceId = deviceId ?: throw AppwinApiException.NotConfigured()
    return AuthSession.bootstrap(
      baseUrl = baseUrl,
      appId = appId,
      deviceId = deviceId,
      externalId = externalId,
      deviceInfo = deviceInfo,
      sdkVersion = VERSION,
      store = secureStore,
    )
  }

  // MARK: analytics entry points (the AppwinAnalytics module is the public
  // surface; the pipeline lives here per ADR-0036 §3, like the iOS `package`
  // level members on AppwinCore)

  @AppwinInternalApi
  public fun analyticsTrack(name: String, props: Map<String, Any?>?) {
    analytics.track(name, props)
  }

  @AppwinInternalApi
  public fun analyticsScreen(name: String) {
    analytics.screen(name)
  }

  @AppwinInternalApi
  public fun analyticsFlush() {
    analytics.flush()
  }

  /** Forwarded to the embedded ad-network adapters (TikTok test events). */
  @AppwinInternalApi
  public fun setAdSignalsDebugMode(enabled: Boolean) {
    analytics.setAdSignalsDebugMode(enabled)
  }

  /**
   * Advertising destination consent (ADR-0038). Callable before
   * [startAttribution]: buffered, applied when attribution starts.
   */
  @AppwinInternalApi
  public fun setAdvertisingConsent(consent: AdvertisingConsent) {
    analytics.setAdvertisingConsent(consent)
  }

  /** Callable before [configure]: buffered, applied when the pipeline is built. */
  @AppwinInternalApi
  public fun analyticsSetConsent(consent: AnalyticsConsent) {
    analytics.setConsent(consent)
  }

  /**
   * Attaches this device to your app's user, for every Appwin product.
   *
   * Call it when the user signs in, and again on each launch if you like: the
   * id is persisted, so it is not required. The anonymous history on this
   * device (conversations, events) is merged into the user server-side.
   *
   * ```kotlin
   * AppwinCore.identify("user-42", AppwinUserAttributes(email = "ada@example.com"))
   * ```
   *
   * @param externalId your stable user id. Must not be blank.
   * @param attributes optional profile fields, applied like [updateUser].
   * @throws IllegalArgumentException if [externalId] is blank.
   * @throws AppwinApiException if the session cannot be opened.
   */
  @JvmStatic
  @JvmOverloads
  public suspend fun identify(externalId: String, attributes: AppwinUserAttributes? = null) {
    identity.identify(externalId, attributes)
  }

  /**
   * Updates the current user's profile without changing who they are. Works
   * for an anonymous user too.
   *
   * Every field of [attributes] is optional; an omitted one is left untouched.
   *
   * @throws AppwinApiException on a network or server error.
   */
  @JvmStatic
  public suspend fun updateUser(attributes: AppwinUserAttributes) {
    identity.updateUser(attributes)
  }

  /**
   * Signs the user out of Appwin: revokes the session, forgets the
   * [externalId] and opens a fresh anonymous session. Call it when the user
   * signs out of **your** app, otherwise the next person on the device
   * inherits their conversations.
   *
   * Never throws: offline, the local state is cleared anyway and the
   * anonymous session opens on the next call that needs one.
   */
  @JvmStatic
  public suspend fun logout() {
    identity.logout()
  }

  /**
   * Registers this device's push token with Appwin. Call again on every token
   * rotation.
   *
   * Uses the Support route so the same table is updated without requiring the
   * Notifications product to be enabled. Shared by Support, Community and
   * Notifications.
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
    identity.registerPushToken(PushTokenBody(token = token, platform = platform, pushOptIn = pushOptIn))
  }

  /**
   * Reminds integrators to register the push token through [registerPushToken].
   *
   * Optional for Support and Community, required for Notifications. A missing
   * token does not block initialization: we log so the omission shows up during
   * integration, not in production silence.
   *
   * Deferred a few seconds: Flutter/Firebase apps typically call
   * [registerPushToken] after [initialize] (once FCM/APNs is ready). Warn only
   * if the token is still missing after that window.
   */
  @JvmStatic
  public fun reportMissingPushToken(product: AppwinProduct) {
    scope.launch {
      kotlinx.coroutines.delay(3_000)
      if (identity.hasRegisteredPushToken) return@launch
      val requirement =
        when (product) {
          AppwinProduct.NOTIFICATIONS -> "required"
          else -> "strongly recommended"
        }
      android.util.Log.w(
        "Appwin",
        "Push token not registered yet ($requirement for ${product.key}). " +
          "Call AppwinCore.registerPushToken(...) after configure, and again on " +
          "every FCM/APNs token rotation.",
      )
    }
  }

  /**
   * Headers added to every outgoing request.
   *
   * The legacy `X-Appwin-*` headers are sent alongside the bearer: the server
   * accepts both, and they are what lets the earliest calls succeed while the
   * bootstrap is still in flight.
   */
  @JvmStatic
  public fun canonicalHeaders(): Map<String, String> {
    val identity = IdentityStore.snapshot()
    val headers = LinkedHashMap<String, String>(6)
    headers["Content-Type"] = "application/json"
    headers["X-Appwin-Platform"] = "android"
    identity.projectAppId?.let { headers["X-Appwin-App-Id"] = it }
    identity.deviceId?.let { headers["X-Appwin-Device-Id"] = it }
    identity.externalId?.let { headers["X-Appwin-User-Id"] = it }
    AuthSession.currentToken(secureStore)?.let { headers["Authorization"] = "Bearer $it" }
    return headers
  }

  /**
   * Shared realtime hub (ADR-0028 §9): one multiplexed WebSocket for the whole
   * app. Created on first access, `null` until [configure] has run.
   */
  @JvmStatic
  public fun realtimeHub(): RealtimeHub? {
    realtimeHub?.let { return it }
    val api = client ?: return null
    var ws = realtimeBaseUrl
      .replace("https://", "wss://")
      .replace("http://", "ws://")
    if (!ws.endsWith("/ws")) ws += "/ws"
    return RealtimeHub.make(gatewayUrl = ws, api = api).also { realtimeHub = it }
  }

  /**
   * Uploads media to S3 through the Core sign-upload flow (ADR-0022).
   *
   * Returns a [UploadedMediaRef] the product passes to its own attach endpoint
   * (Support message, Community post, …). Image compression is skipped for now;
   * bytes go through as-is, matching a `compression: nil` iOS call.
   */
  @AppwinInternalApi
  public suspend fun uploadMedia(
    data: ByteArray,
    mimeType: String,
    filename: String,
    onProgress: (Double) -> Unit = {},
  ): UploadedMediaRef {
    val api = client ?: throw AppwinApiException.NotConfigured()

    // sign-upload needs a bearer. configure() bootstraps in the background, but
    // a first attach right after launch can race it - force it here (idempotent).
    if (AuthSession.currentToken(secureStore) == null) bootstrapSession()

    return MediaUploads.upload(api, data, mimeType, filename, onProgress)
  }

  /**
   * Multipart POST to an already-signed S3 policy. Products that sign via their
   * own endpoints (Community uploads) call this after obtaining postUrl/fields.
   */
  @AppwinInternalApi
  public suspend fun uploadPresigned(
    data: ByteArray,
    mimeType: String,
    filename: String,
    postUrl: String,
    fields: Map<String, String>,
    onProgress: (Double) -> Unit = {},
  ) {
    BucketUploader.upload(
      data = data,
      mimeType = mimeType,
      filename = filename,
      postUrl = postUrl,
      fields = fields,
      onProgress = onProgress,
    )
  }

  /** Test-only: the object is a singleton, so one test would leak into the next. */
  internal fun resetForTesting() {
    IdentityStore.reset()
    analytics.reset()
    appContext = null
    secureStore = null
    client = null
    deviceInfo = null
    identity.reset()
    realtimeHub = null
    baseUrl = "https://api.appwin.io"
    realtimeBaseUrl = "https://ws.appwin.io"
    AppwinPush.resetForTesting()
  }
}
