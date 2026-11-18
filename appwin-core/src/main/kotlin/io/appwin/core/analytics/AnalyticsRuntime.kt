package io.appwin.core.analytics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import io.appwin.core.AppForeground
import io.appwin.core.AppwinInternalApi
import io.appwin.core.analytics.crash.ApiCrashSender
import io.appwin.core.analytics.crash.Breadcrumbs
import io.appwin.core.analytics.crash.CrashContext
import io.appwin.core.analytics.crash.CrashReporter
import io.appwin.core.analytics.crash.CrashStore
import io.appwin.core.analytics.crash.ExitInfoCollector
import io.appwin.core.analytics.crash.InAppClassifier
import io.appwin.core.attribution.AdIdentityReporter
import io.appwin.core.attribution.AdSignalsHub
import io.appwin.core.attribution.AdvertisingConsent
import io.appwin.core.attribution.InstallReferrerCollector
import io.appwin.core.consent.ConsentPurpose
import io.appwin.core.consent.ConsentState
import io.appwin.core.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * The event pipeline and the acquisition signals riding on it (ADR-0036 §3,
 * ADR-0038). Shared by Analytics and Attribution, neither product depending
 * on the other; `AppwinCore` only forwards to it.
 */
@OptIn(AppwinInternalApi::class)
internal class AnalyticsRuntime(
  private val scope: CoroutineScope,
  private val client: () -> ApiClient?,
  private val reauthorize: suspend () -> Boolean,
) {
  /** What the pipeline needs from `configure`; null before it. */
  class Host(
    val context: Context,
    val projectAppId: String,
    val appVersion: String?,
    /** The device id existed before the first `configure` (see `AppwinCore`). */
    val deviceKnownBeforeConfigure: Boolean = false,
  )

  @Volatile
  private var pipeline: EventPipeline? = null

  /** True once `configure` (or a product) built the pipeline. */
  internal val isPipelineStarted: Boolean
    get() = pipeline != null

  /**
   * Guards [consentStore] and [pendingConsents] together: a `setConsent`
   * racing the pipeline's creation lands in the store or in the list, never
   * between, and always in call order.
   */
  private val consentLock = Any()

  /**
   * Consents set before `configure` (a consent-screen studio starting in
   * UNKNOWN), in call order. Written to the store, in that order, when it is
   * built, so nothing can slip out in between.
   */
  private val pendingConsents = mutableListOf<Pair<ConsentState, ConsentPurpose?>>()

  /**
   * Parts outside Core that must hear of every consent change (the replay
   * recorder in the Analytics module). Registered once per process by their
   * owner, so [reset] leaves them alone.
   */
  private val consentListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

  fun addConsentListener(listener: () -> Unit): AutoCloseable {
    consentListeners.add(listener)
    return AutoCloseable { consentListeners.remove(listener) }
  }

  /** Subscriptions made by the runtime itself, closed by [reset]. */
  private val busSubscriptions = mutableListOf<AutoCloseable>()

  /**
   * The internal bus (not public for now): `track` and the automatic events
   * publish here, and each subscriber hears an event only when the purpose it
   * needs is granted at that moment.
   */
  internal val eventBus = EventBus { consentState(it) == ConsentState.GRANTED }

  /**
   * No sign of an earlier install when the pipeline was built (unknown
   * device, no install flag, no 0.15.0 referrer flag). Frozen before the
   * first session writes its flag.
   */
  @Volatile
  var installIsFresh: Boolean = false
    private set

  @Volatile
  private var consentStore: ConsentStore? = null

  @Volatile
  private var crashes: CrashReporter? = null

  @Volatile
  private var adIdentity: AdIdentityReporter? = null

  @Volatile
  private var adSignals: AdSignalsHub? = null

  /** Same buffering story as [pendingConsents], advertising side. */
  @Volatile
  private var pendingAdvertisingConsent: AdvertisingConsent? = null

  /**
   * Read by the hub at activation, so it must be set before Attribution
   * initializes: the network SDKs cannot re-init once started.
   */
  @Volatile
  private var adSignalsDebug: Boolean = false

  /** Session of the pipeline, for the replay recorder; null before the first foreground. */
  val currentSessionId: String?
    get() = pipeline?.currentSessionId

  /**
   * The answer for [purpose], read from the store (the single source), or,
   * before `configure`, from the consents waiting for it.
   */
  fun consentState(purpose: ConsentPurpose): ConsentState = synchronized(consentLock) {
    consentStore?.state(purpose) ?: pendingConsents.fold(ConsentState.GRANTED) { current, (state, target) ->
      if (target == null || target == purpose) state else current
    }
  }

  fun startAttribution(host: Host) {
    if (adIdentity != null) return
    val pipeline = ensurePipeline(host)
    val prefs = SharedPrefsAnalyticsPrefs(host.context)
    val keyPrefix = keyPrefix(host)

    // Play Install Referrer: one-shot install-source capture, emitted as a
    // reserved event so consent and batching apply unchanged.
    InstallReferrerCollector(
      context = host.context,
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

    // GAID under the advertising consent, on its own channel - never in the
    // event stream.
    val reporter = AdIdentityReporter(
      context = host.context,
      prefs = prefs,
      keyPrefix = keyPrefix,
      client = client,
      reauthorize = reauthorize,
      scope = scope,
    )
    adIdentity = reporter
    pendingAdvertisingConsent?.let { reporter.setConsent(it) }
    pendingAdvertisingConsent = null
    reporter.start()

    val hub = AdSignalsHub(
      context = host.context,
      client = client,
      consent = { reporter.consent },
      scope = scope,
      debugMode = { adSignalsDebug },
    )
    adSignals = hub
    hub.start()
    wireAdSignals(hub::onEvent)
  }

  /** The ad networks hear the stream under `ADVERTISING` (and their own `setAdvertisingConsent`). */
  internal fun wireAdSignals(onEvent: (name: String, eventId: String, props: Map<String, Any?>?) -> Unit) {
    synchronized(busSubscriptions) {
      busSubscriptions += eventBus.subscribe(ConsentPurpose.ADVERTISING) {
        onEvent(it.name, it.eventId, it.props)
      }
    }
  }

  /** Crash breadcrumbs follow the stream under `ANALYTICS`. */
  internal fun wireCrashBreadcrumbs(onEvent: (name: String) -> Unit) {
    synchronized(busSubscriptions) {
      busSubscriptions += eventBus.subscribe(ConsentPurpose.ANALYTICS) { onEvent(it.name) }
    }
  }

  /** Created once, by the first product that needs it, then reused. */
  @Synchronized
  fun ensurePipeline(host: Host): EventPipeline {
    pipeline?.let { return it }
    val config = AnalyticsConfig()
    val prefs = SharedPrefsAnalyticsPrefs(host.context)
    val keyPrefix = keyPrefix(host)
    installIsFresh = SessionManager.isFreshInstall(prefs, keyPrefix, host.deviceKnownBeforeConfigure)
    val consent = synchronized(consentLock) {
      ConsentStore(prefs, keyPrefix).also { store ->
        pendingConsents.forEach { (state, purpose) -> store.set(state, purpose) }
        pendingConsents.clear()
        consentStore = store
      }
    }
    val created = EventPipeline(
      store = EventStore(
        directory = File(host.context.filesDir, "appwin/analytics/${host.projectAppId}"),
        maxBatch = config.maxBatch,
        maxQueueEvents = config.maxQueueEvents,
      ),
      sessions = SessionManager(
        prefs = prefs,
        keyPrefix = keyPrefix,
        appVersion = host.appVersion,
        timeoutMs = config.sessionTimeoutMs,
        maxAgeMs = config.maxSessionAgeMs,
        deviceKnownBeforeConfigure = host.deviceKnownBeforeConfigure,
      ),
      consentStore = consent,
      sender = ApiEventSender(client),
      config = config,
      prefs = prefs,
      keyPrefix = keyPrefix,
      reauthorize = reauthorize,
      scope = scope,
    )
    pipeline = created
    created.start()

    // The one lifecycle observer lives in AppForeground. Its launch
    // emission is not replayed: subscribe undispatched, before configure
    // installs it, so the launch opens the session (and emits app_install on
    // a first run).
    // One coroutine over the one ordered stream: a stop then a start must
    // reach the pipeline as Background then Foreground.
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      AppForeground.transitions.collect {
        created.submit(
          when (it) {
            AppForeground.Transition.FOREGROUND -> EventPipeline.Command.Foreground
            AppForeground.Transition.BACKGROUND -> EventPipeline.Command.Background
          },
        )
      }
    }

    // The offline -> online edge cuts a pending backoff short. Best effort:
    // the SDK must not fall over for a missing permission on an exotic host.
    runCatching {
      val connectivity =
        host.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      connectivity.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            created.submit(EventPipeline.Command.NetworkRegained)
            crashes?.flush(resetBackoff = true)
          }
        },
      )
    }

    return created
  }

  /**
   * Crash capture (ADR-0056), on top of the pipeline whose consent and
   * session it reads. Idempotent.
   */
  fun startCrashReporting(host: Host, sdkVersion: String, inAppPackages: List<String>) {
    if (crashes != null) return
    val pipeline = ensurePipeline(host)
    val consent = consentStore ?: return
    val prefs = SharedPrefsAnalyticsPrefs(host.context)
    val classifier = InAppClassifier(listOf(host.context.packageName) + inAppPackages)
    val reporter = CrashReporter(
      store = CrashStore(
        File(host.context.filesDir, "appwin/analytics/${host.projectAppId}/crashes"),
      ),
      sender = ApiCrashSender(client),
      breadcrumbs = Breadcrumbs(),
      classifier = classifier,
      crashContext = crashContext(host, sdkVersion),
      storedConsent = { consent.stateWithoutBlocking(ConsentPurpose.ANALYTICS) },
      sessionId = { pipeline.currentSessionId },
      reauthorize = reauthorize,
      scope = scope,
    )
    crashes = reporter
    wireCrashBreadcrumbs(reporter::onEvent)
    CrashReporter.install(reporter)
    reporter.start(ExitInfoCollector(host.context, prefs, keyPrefix(host), classifier))
  }

  /** Non-fatal error; dropped with a log when crash reporting is off. */
  fun recordError(throwable: Throwable) {
    val reporter = crashes ?: return reportMisuse("crash reporting is not started")
    reporter.recordError(throwable)
  }

  /** Dart or JS error from a cross-platform plugin; persisted before returning. */
  fun recordBridgedError(
    runtime: String,
    fatal: Boolean,
    type: String,
    message: String?,
    frames: List<Map<String, Any?>>,
  ) {
    val reporter = crashes ?: return reportMisuse("crash reporting is not started")
    reporter.recordBridged(runtime, fatal, type, message, frames)
  }

  fun track(name: String, props: Map<String, Any?>?) {
    val pipeline = pipeline ?: return reportMisuse("configure has not been called")
    if (!AnalyticsValidation.isValidEventName(name) || name in AnalyticsValidation.RESERVED_NAMES) {
      return reportMisuse(
        "invalid event name '$name' (expected ^[a-z][a-z0-9_]{0,63}$, not reserved)",
      )
    }
    val sanitized = AnalyticsValidation.sanitizeProps(props)
    // Generated here, not at enqueue time: the ad-signals adapters must
    // carry the SAME id as our ingest (cross-source dedup, ADR-0038).
    val eventId = UUID.randomUUID().toString().lowercase()
    pipeline.submit(
      EventPipeline.Command.Track(
        name = name,
        screen = null,
        props = sanitized,
        occurredAtMs = System.currentTimeMillis(),
        eventId = eventId,
      ),
    )
    eventBus.publish(EventBus.Event(name, eventId, sanitized))
  }

  /**
   * An event the SDK raises itself: reserved names allowed, same ride-alongs
   * as [track]. Dropped quietly before `configure`.
   */
  fun emitAutomatic(name: String, props: Map<String, Any?>?) {
    val pipeline = pipeline ?: return reportMisuse("configure has not been called")
    val sanitized = AnalyticsValidation.sanitizeProps(props)
    val eventId = UUID.randomUUID().toString().lowercase()
    pipeline.submit(
      EventPipeline.Command.Automatic(
        name = name,
        props = sanitized,
        occurredAtMs = System.currentTimeMillis(),
        eventId = eventId,
      ),
    )
    eventBus.publish(EventBus.Event(name, eventId, sanitized))
  }

  fun screen(name: String) {
    val pipeline = pipeline ?: return reportMisuse("configure has not been called")
    val screen = name.take(AnalyticsValidation.MAX_SCREEN_LENGTH)
    pipeline.submit(
      EventPipeline.Command.Track(
        name = "screen_view",
        screen = screen,
        props = null,
        occurredAtMs = System.currentTimeMillis(),
      ),
    )
    crashes?.onScreen(screen)
  }

  fun flush() {
    pipeline?.submit(EventPipeline.Command.Flush)
    crashes?.flush()
  }

  /**
   * Writes the answer to the store now, in call order, then tells the
   * pipeline and the crash reporter that it changed. They carry no value:
   * each re-reads the store when it runs, so a command handled late can't
   * apply a stale answer.
   */
  fun setConsent(consent: ConsentState, purpose: ConsentPurpose? = null) {
    synchronized(consentLock) {
      val store = consentStore
      if (store == null) {
        // Legal, not a misuse: a consent-screen studio sets UNKNOWN before
        // `configure` so the very first events cannot leave under the
        // opt-out default.
        pendingConsents += consent to purpose
        return
      }
      store.set(consent, purpose)
    }
    pipeline?.submit(EventPipeline.Command.ConsentChanged)
    crashes?.onConsentChanged()
    for (listener in consentListeners) {
      runCatching { listener() }
        .onFailure { android.util.Log.w("Appwin", "analytics: a consent listener failed", it) }
    }
  }

  fun setAdvertisingConsent(consent: AdvertisingConsent) {
    val reporter = adIdentity
    if (reporter == null) {
      pendingAdvertisingConsent = consent
      return
    }
    reporter.setConsent(consent)
    adSignals?.onConsentChanged()
  }

  fun setAdSignalsDebugMode(enabled: Boolean) {
    adSignalsDebug = enabled
  }

  fun reset() {
    pipeline?.stop()
    pipeline = null
    synchronized(consentLock) {
      consentStore = null
      pendingConsents.clear()
    }
    synchronized(busSubscriptions) {
      busSubscriptions.forEach { it.close() }
      busSubscriptions.clear()
    }
    crashes = null
    CrashReporter.active = null
    installIsFresh = false
    adIdentity = null
    adSignals = null
    pendingAdvertisingConsent = null
  }

  private fun keyPrefix(host: Host) = "appwin.analytics.${host.projectAppId}."

  private fun crashContext(host: Host, sdkVersion: String): CrashContext {
    val build = runCatching {
      val info = host.context.packageManager.getPackageInfo(host.context.packageName, 0)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toString()
      else @Suppress("DEPRECATION") info.versionCode.toString()
    }.getOrNull()
    return CrashContext(
      appVersion = host.appVersion ?: "unknown",
      appBuild = build,
      os = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}",
      model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "unknown" },
      sdkVersion = sdkVersion,
    )
  }

  /**
   * An integration mistake must surface during development and never crash
   * a shipped app: log-and-drop.
   */
  private fun reportMisuse(detail: String) {
    android.util.Log.w("Appwin", "analytics: $detail")
  }
}

