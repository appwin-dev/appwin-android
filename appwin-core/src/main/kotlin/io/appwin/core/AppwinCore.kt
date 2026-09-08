package io.appwin.core

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.appwin.core.analytics.AnalyticsConfig
import io.appwin.core.analytics.AnalyticsConsent
import io.appwin.core.analytics.AnalyticsValidation
import io.appwin.core.analytics.ApiEventSender
import io.appwin.core.analytics.ConsentStore
import io.appwin.core.analytics.EventPipeline
import io.appwin.core.analytics.EventStore
import io.appwin.core.analytics.SessionManager
import io.appwin.core.analytics.SharedPrefsAnalyticsPrefs
import io.appwin.core.attribution.InstallReferrerCollector
import io.appwin.core.identity.DeviceInfo
import io.appwin.core.inapp.AppwinInAppBanner
import java.io.File
import io.appwin.core.identity.IdentityStore
import io.appwin.core.identity.SecureStore
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.AvailabilityStore
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import io.appwin.core.network.RealtimeHub
import io.appwin.core.push.PushTokenBody
import io.appwin.core.session.AuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
  public const val VERSION: String = "0.1.0-dev"

  private const val DEVICE_ID_KEY = "appwin.core.deviceId"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val bootstrapMutex = Mutex()

  private var secureStore: SecureStore? = null
  private var availabilityStore: AvailabilityStore? = null
  private var inFlightBootstrap: Deferred<String>? = null
  private var realtimeHub: RealtimeHub? = null
  private var pushTokenRegistered = false

  @Volatile
  private var analytics: EventPipeline? = null

  /**
   * Consent set before [configure] (a consent-screen studio starting in
   * UNKNOWN). Applied synchronously when the pipeline is built, so nothing
   * can slip out in between.
   */
  @Volatile
  private var pendingAnalyticsConsent: AnalyticsConsent? = null

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

  /** User id supplied by the host app. `null` means anonymous. */
  public val externalId: String?
    get() = IdentityStore.snapshot().externalId

  /** Whether [registerPushToken] has succeeded at least once this process. */
  public val hasRegisteredPushToken: Boolean
    get() = pushTokenRegistered

  /**
   * Call once at launch, before using any product module. Idempotent.
   *
   * Synchronous: it prepares the device identity and network client right
   * away, then opens the session in the background so the app keeps starting.
   * Await [bootstrapSession] to require an open session.
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

    IdentityStore.mutate { it.copy(projectAppId = projectAppId, deviceId = deviceId) }
    deviceInfo = DeviceInfo.current(context)

    client = ApiClient(baseUrl = this.baseUrl, headersProvider = ::canonicalHeaders)
    appContext = context.applicationContext

    applicationContext = context.applicationContext

    // The shared in-app surface, so a product that wants to show a banner has
    // one to show it on. Nothing is drawn until a product asks.
    (context.applicationContext as? Application)?.let(AppwinInAppBanner::install)

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
    val context = appContext ?: return
    val projectAppId = projectAppId ?: return
    if (analytics != null) return
    val config = AnalyticsConfig()
    val prefs = SharedPrefsAnalyticsPrefs(context)
    val keyPrefix = "appwin.analytics.$projectAppId."
    val pipeline = EventPipeline(
      store = EventStore(
        directory = File(context.filesDir, "appwin/analytics/$projectAppId"),
        maxBatch = config.maxBatch,
        maxQueueEvents = config.maxQueueEvents,
      ),
      sessions = SessionManager(
        prefs = prefs,
        keyPrefix = keyPrefix,
        appVersion = deviceInfo?.appVersion,
        timeoutMs = config.sessionTimeoutMs,
        maxAgeMs = config.maxSessionAgeMs,
      ),
      consentStore = ConsentStore(prefs, keyPrefix, initial = pendingAnalyticsConsent),
      sender = ApiEventSender { client },
      config = config,
      prefs = prefs,
      keyPrefix = keyPrefix,
      reauthorize = { runCatching { bootstrapSession() }.isSuccess },
      scope = scope,
    )
    analytics = pipeline
    pipeline.start()

    // ProcessLifecycleOwner delivers ON_START right away on an already
    // started process, so the launch itself opens the session (and emits
    // app_install on a first run). Registration must happen on the main
    // thread; runCatching because a unit-test host has no lifecycle.
    val register = Runnable {
      runCatching {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
          object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
              pipeline.submit(EventPipeline.Command.Foreground)
            }

            override fun onStop(owner: LifecycleOwner) {
              pipeline.submit(EventPipeline.Command.Background)
            }
          },
        )
      }.onFailure {
        android.util.Log.w("Appwin", "analytics: no process lifecycle, sessions cut on relaunch only")
      }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) register.run()
    else Handler(Looper.getMainLooper()).post(register)

    // The offline -> online edge cuts a pending backoff short. Best effort:
    // the SDK must not fall over for a missing permission on an exotic host.
    runCatching {
      val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      connectivity.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            pipeline.submit(EventPipeline.Command.NetworkRegained)
          }
        },
      )
    }

    // Play Install Referrer (ADR-0038): one-shot install-source capture,
    // emitted as a reserved event so consent and batching apply unchanged.
    InstallReferrerCollector(
      context = context,
      prefs = prefs,
      keyPrefix = keyPrefix,
      emit = { name, props ->
        pipeline.submit(
          EventPipeline.Command.Track(
            name = name,
            screen = null,
            props = AnalyticsValidation.sanitizeProps(props),
            occurredAtMs = System.currentTimeMillis(),
          ),
        )
      },
    ).start()
  }

  /**
   * Opens the server session and returns the token.
   *
   * Concurrent callers share the **same** network call. Not an optimisation:
   * `/auth/init` rotates the token and invalidates the previous one, so two
   * simultaneous inits - the one [configure] spawns and an explicitly awaited
   * one - revoke each other's token, which shows up as intermittent 401s at
   * startup.
   */

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

  public suspend fun bootstrapSession(externalId: String? = null): String {
    val appId = projectAppId ?: throw AppwinApiException.NotConfigured()
    val deviceId = deviceId ?: throw AppwinApiException.NotConfigured()

    val task = bootstrapMutex.withLock {
      inFlightBootstrap ?: scope.async {
        AuthSession.bootstrap(
          baseUrl = baseUrl,
          appId = appId,
          deviceId = deviceId,
          externalId = externalId ?: this@AppwinCore.externalId,
          deviceInfo = deviceInfo,
          sdkVersion = VERSION,
          store = secureStore,
        )
      }.also { inFlightBootstrap = it }
    }

    return try {
      task.await()
    } finally {
      bootstrapMutex.withLock {
        if (inFlightBootstrap === task) inFlightBootstrap = null
      }
    }
  }

  // MARK: analytics entry points (the AppwinAnalytics module is the public
  // surface; the pipeline lives here per ADR-0036 §3, like the iOS `package`
  // level members on AppwinCore)

  @AppwinInternalApi
  public fun analyticsTrack(name: String, props: Map<String, Any?>?) {
    val pipeline = analytics ?: return reportAnalyticsMisuse("configure has not been called")
    if (!AnalyticsValidation.isValidEventName(name) || name in AnalyticsValidation.RESERVED_NAMES) {
      return reportAnalyticsMisuse(
        "invalid event name '$name' (expected ^[a-z][a-z0-9_]{0,63}$, not reserved)",
      )
    }
    pipeline.submit(
      EventPipeline.Command.Track(
        name = name,
        screen = null,
        props = AnalyticsValidation.sanitizeProps(props),
        occurredAtMs = System.currentTimeMillis(),
      ),
    )
  }

  @AppwinInternalApi
  public fun analyticsScreen(name: String) {
    val pipeline = analytics ?: return reportAnalyticsMisuse("configure has not been called")
    pipeline.submit(
      EventPipeline.Command.Track(
        name = "screen_view",
        screen = name.take(AnalyticsValidation.MAX_SCREEN_LENGTH),
        props = null,
        occurredAtMs = System.currentTimeMillis(),
      ),
    )
  }

  @AppwinInternalApi
  public fun analyticsFlush() {
    analytics?.submit(EventPipeline.Command.Flush)
  }

  @AppwinInternalApi
  public fun analyticsSetConsent(consent: AnalyticsConsent) {
    val pipeline = analytics
    if (pipeline == null) {
      // Legal, not a misuse: a consent-screen studio sets UNKNOWN before
      // `configure` so the very first events cannot leave under the
      // opt-out default.
      pendingAnalyticsConsent = consent
      return
    }
    pipeline.submit(EventPipeline.Command.SetConsent(consent))
  }

  /**
   * An integration mistake must surface during development and never crash
   * a shipped app: log-and-drop, like [reportMissingPushToken].
   */
  private fun reportAnalyticsMisuse(detail: String) {
    android.util.Log.w("Appwin", "analytics: $detail")
  }

  /**
   * Attaches the device to the host app's user. The identity is shared by
   * every active product module.
   */
  @JvmStatic
  public fun identify(externalId: String) {
    IdentityStore.mutate { it.copy(externalId = externalId) }
  }

  /** Goes back to anonymous locally, without revoking the server session. */
  @JvmStatic
  public fun clearIdentity() {
    IdentityStore.mutate { it.copy(externalId = null) }
  }

  /**
   * Revokes the session server-side, clears the local token and goes back to
   * anonymous. Call it when the user signs out of **your** app, otherwise the
   * next person on the device inherits their identity.
   */
  public suspend fun signOut() {
    AuthSession.signOut(baseUrl, secureStore)
    IdentityStore.mutate { it.copy(externalId = null) }
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
    val api = client ?: throw AppwinApiException.NotConfigured()
    api.requestVoid(
      path = "/api/sdk/support/v1/push-token",
      method = HttpMethod.POST,
      body = ApiClient.json.encodeToString(
        PushTokenBody.serializer(),
        PushTokenBody(token = token, platform = platform, pushOptIn = pushOptIn),
      ),
    )
    pushTokenRegistered = true
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
      if (pushTokenRegistered) return@launch
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

  /** Test-only: the object is a singleton, so one test would leak into the next. */
  internal fun resetForTesting() {
    IdentityStore.reset()
    analytics?.stop()
    analytics = null
    pendingAnalyticsConsent = null
    appContext = null
    secureStore = null
    client = null
    deviceInfo = null
    inFlightBootstrap = null
    realtimeHub = null
    pushTokenRegistered = false
    baseUrl = "https://api.appwin.io"
    realtimeBaseUrl = "https://ws.appwin.io"
  }
}
