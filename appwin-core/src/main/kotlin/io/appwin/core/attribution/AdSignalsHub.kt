package io.appwin.core.attribution

import android.content.Context
import android.util.Log
import io.appwin.core.network.ApiClient
import io.appwin.core.network.HttpMethod
import java.util.ServiceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Orchestrates the ad-signals adapters (ADR-0038): loads whatever
 * adapter modules the app build ships (ServiceLoader), fetches the
 * dashboard wiring once per start, and reconciles - an adapter runs
 * exactly when its network is wired AND the advertising consent is
 * granted. Everything is best effort: a broken adapter must never take
 * the host app down.
 */
@Serializable
internal data class ActivationConfigResponse(
  val networks: Map<String, Map<String, String>> = emptyMap(),
)

internal class AdSignalsHub(
  private val context: Context,
  private val client: () -> ApiClient?,
  private val consent: () -> AdvertisingConsent,
  private val scope: CoroutineScope,
  private val debugMode: () -> Boolean = { false },
  private val adapters: List<AdSignalsAdapter> = loadAdapters(),
) {
  @Volatile private var configs: Map<String, Map<String, String>> = emptyMap()
  private val active = HashSet<String>()

  fun start() {
    if (adapters.isEmpty()) return
    scope.launch {
      fetchConfig()
      reconcile()
    }
  }

  fun onConsentChanged() {
    if (adapters.isEmpty()) return
    scope.launch { reconcile() }
  }

  fun onEvent(name: String, eventId: String, props: Map<String, Any?>?) {
    if (active.isEmpty()) return
    for (adapter in adapters) {
      if (adapter.network !in active) continue
      runCatching { adapter.onEvent(name, eventId, props) }
        .onFailure { Log.w(TAG, "ad-signals: ${adapter.network} onEvent failed", it) }
    }
  }

  private suspend fun fetchConfig() {
    val api = client() ?: return
    configs = runCatching {
      api.request(
        "/api/sdk/v1/attribution/activation-config",
        HttpMethod.GET,
        ActivationConfigResponse.serializer(),
      ).networks
    }.getOrElse { emptyMap() }
  }

  /** Idempotent per adapter: activation state changes fire the hooks once. */
  @Synchronized
  private fun reconcile() {
    val granted = consent() == AdvertisingConsent.GRANTED
    val debug = debugMode()
    for (adapter in adapters) {
      // Test-events mode (studio-requested, before initialize): the
      // adapter forwards it to its network SDK. Data sent in this mode
      // is flagged as test by the network and excluded from campaigns.
      val config = configs[adapter.network]?.let { if (debug) it + ("debugMode" to "true") else it }
      val shouldRun = granted && config != null
      val isRunning = adapter.network in active
      if (shouldRun && !isRunning) {
        runCatching { adapter.activate(context, config ?: emptyMap()) }
          .onSuccess { active.add(adapter.network) }
          .onFailure { Log.w(TAG, "ad-signals: ${adapter.network} activation failed", it) }
      } else if (!shouldRun && isRunning) {
        runCatching { adapter.deactivate() }
        active.remove(adapter.network)
      }
    }
  }

  private companion object {
    const val TAG = "Appwin"
  }
}

/** Adapter modules self-declare via META-INF/services; no studio code. */
private fun loadAdapters(): List<AdSignalsAdapter> =
  runCatching {
    ServiceLoader.load(AdSignalsAdapter::class.java, AdSignalsAdapter::class.java.classLoader)
      .toList()
  }.getOrElse { emptyList() }
