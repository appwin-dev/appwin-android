package io.appwin.core.analytics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.appwin.core.attribution.AdIdentityReporter
import io.appwin.core.attribution.AdSignalsHub
import io.appwin.core.attribution.AdvertisingConsent
import io.appwin.core.attribution.InstallReferrerCollector
import io.appwin.core.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.UUID

/**
 * The event pipeline and the acquisition signals riding on it (ADR-0036 §3,
 * ADR-0038). Shared by Analytics and Attribution, neither product depending
 * on the other; `AppwinCore` only forwards to it.
 */
internal class AnalyticsRuntime(
  private val scope: CoroutineScope,
  private val client: () -> ApiClient?,
  private val reauthorize: suspend () -> Boolean,
) {
  /** What the pipeline needs from `configure`; null before it. */
  class Host(val context: Context, val projectAppId: String, val appVersion: String?)

  @Volatile
  private var pipeline: EventPipeline? = null

  /**
   * Consent set before `configure` (a consent-screen studio starting in
   * UNKNOWN). Applied synchronously when the pipeline is built, so nothing
   * can slip out in between.
   */
  @Volatile
  private var pendingAnalyticsConsent: AnalyticsConsent? = null

  @Volatile
  private var adIdentity: AdIdentityReporter? = null

  @Volatile
  private var adSignals: AdSignalsHub? = null

  /** Same buffering story as [pendingAnalyticsConsent], advertising side. */
  @Volatile
  private var pendingAdvertisingConsent: AdvertisingConsent? = null

  /**
   * Read by the hub at activation, so it must be set before Attribution
   * initializes: the network SDKs cannot re-init once started.
   */
  @Volatile
  private var adSignalsDebug: Boolean = false

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
  }

  /** Created once, by the first product that needs it, then reused. */
  fun ensurePipeline(host: Host): EventPipeline {
    pipeline?.let { return it }
    val config = AnalyticsConfig()
    val prefs = SharedPrefsAnalyticsPrefs(host.context)
    val keyPrefix = keyPrefix(host)
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
      ),
      consentStore = ConsentStore(prefs, keyPrefix, initial = pendingAnalyticsConsent),
      sender = ApiEventSender(client),
      config = config,
      prefs = prefs,
      keyPrefix = keyPrefix,
      reauthorize = reauthorize,
      scope = scope,
    )
    pipeline = created
    created.start()

    // ProcessLifecycleOwner delivers ON_START right away on an already
    // started process, so the launch itself opens the session (and emits
    // app_install on a first run). Registration must happen on the main
    // thread; runCatching because a unit-test host has no lifecycle.
    val register = Runnable {
      runCatching {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
          object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
              created.submit(EventPipeline.Command.Foreground)
            }

            override fun onStop(owner: LifecycleOwner) {
              created.submit(EventPipeline.Command.Background)
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
      val connectivity =
        host.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      connectivity.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            created.submit(EventPipeline.Command.NetworkRegained)
          }
        },
      )
    }

    return created
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
    adSignals?.onEvent(name, eventId, sanitized)
  }

  fun screen(name: String) {
    val pipeline = pipeline ?: return reportMisuse("configure has not been called")
    pipeline.submit(
      EventPipeline.Command.Track(
        name = "screen_view",
        screen = name.take(AnalyticsValidation.MAX_SCREEN_LENGTH),
        props = null,
        occurredAtMs = System.currentTimeMillis(),
      ),
    )
  }

  fun flush() {
    pipeline?.submit(EventPipeline.Command.Flush)
  }

  fun setConsent(consent: AnalyticsConsent) {
    val pipeline = pipeline
    if (pipeline == null) {
      // Legal, not a misuse: a consent-screen studio sets UNKNOWN before
      // `configure` so the very first events cannot leave under the
      // opt-out default.
      pendingAnalyticsConsent = consent
      return
    }
    pipeline.submit(EventPipeline.Command.SetConsent(consent))
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
    pendingAnalyticsConsent = null
    adIdentity = null
    adSignals = null
    pendingAdvertisingConsent = null
  }

  private fun keyPrefix(host: Host) = "appwin.analytics.${host.projectAppId}."

  /**
   * An integration mistake must surface during development and never crash
   * a shipped app: log-and-drop.
   */
  private fun reportMisuse(detail: String) {
    android.util.Log.w("Appwin", "analytics: $detail")
  }
}
