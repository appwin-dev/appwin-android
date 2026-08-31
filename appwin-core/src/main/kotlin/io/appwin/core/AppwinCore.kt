package io.appwin.core

import android.content.Context
import io.appwin.core.identity.DeviceInfo
import io.appwin.core.identity.IdentityStore
import io.appwin.core.identity.SecureStore
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
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
  private var inFlightBootstrap: Deferred<String>? = null

  public var baseUrl: String = "https://api.appwin.io"
    private set

  /** Realtime service base URL. A **separate** service from the API. */
  public var realtimeBaseUrl: String = "https://ws.appwin.io"
    private set

  public var deviceInfo: DeviceInfo? = null
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

    val deviceId = store.get(DEVICE_ID_KEY) ?: UUID.randomUUID().toString().also {
      store.set(DEVICE_ID_KEY, it)
    }

    IdentityStore.mutate { it.copy(projectAppId = projectAppId, deviceId = deviceId) }
    deviceInfo = DeviceInfo.current(context)

    client = ApiClient(baseUrl = this.baseUrl, headersProvider = ::canonicalHeaders)

    scope.launch { runCatching { bootstrapSession() } }
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

  /** Test-only: the object is a singleton, so one test would leak into the next. */
  internal fun resetForTesting() {
    IdentityStore.reset()
    secureStore = null
    client = null
    deviceInfo = null
    inFlightBootstrap = null
    baseUrl = "https://api.appwin.io"
    realtimeBaseUrl = "https://ws.appwin.io"
  }
}
