package io.appwin.core.attribution

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import io.appwin.core.analytics.AnalyticsPrefs

/**
 * One-shot Play Install Referrer capture (ADR-0038, measure phase 1):
 * Android's install-source signal, the counterpart of SKAN on iOS.
 *
 * The answer rides the regular pipeline as the reserved
 * `install_referrer` event, so consent gating, batching and retries
 * apply unchanged and the backend needs no new endpoint. Definitive
 * store answers (including "no Play Store") burn the one shot;
 * transient ones leave it for the next launch.
 */
internal class InstallReferrerCollector(
  private val context: Context,
  private val prefs: AnalyticsPrefs,
  private val keyPrefix: String,
  private val emit: (name: String, props: Map<String, Any?>) -> Unit,
  private val clientFactory: (Context) -> InstallReferrerClient = {
    InstallReferrerClient.newBuilder(it).build()
  },
) {
  fun start() {
    if (prefs.getString(keyPrefix + TRACKED_KEY) != null) return
    // runCatching everywhere: the SDK must not fall over for a broken
    // Play Store binding on an exotic host.
    val client = runCatching { clientFactory(context) }.getOrElse { return }
    runCatching {
      client.startConnection(
        object : InstallReferrerStateListener {
          override fun onInstallReferrerSetupFinished(responseCode: Int) {
            runCatching { handle(client, responseCode) }
              .onFailure { Log.w(TAG, "attribution: install referrer failed", it) }
            runCatching { client.endConnection() }
          }

          // An in-process retry would fight the store; next launch retries.
          override fun onInstallReferrerServiceDisconnected() {}
        },
      )
    }.onFailure { Log.w(TAG, "attribution: install referrer connection failed", it) }
  }

  private fun handle(client: InstallReferrerClient, responseCode: Int) {
    when (responseCode) {
      InstallReferrerClient.InstallReferrerResponse.OK -> {
        val details = client.installReferrer
        val props = HashMap<String, Any?>(5)
        props["referrer_url"] = details.installReferrer.orEmpty().take(MAX_PROP_STRING)
        if (details.referrerClickTimestampSeconds > 0) {
          props["referrer_click_ts"] = details.referrerClickTimestampSeconds
        }
        if (details.installBeginTimestampSeconds > 0) {
          props["install_begin_ts"] = details.installBeginTimestampSeconds
        }
        props["google_play_instant"] = details.googlePlayInstantParam
        details.installVersion?.let { props["install_version"] = it }
        prefs.putString(keyPrefix + TRACKED_KEY, "1")
        emit("install_referrer", props)
      }
      // Whatever the store answers tomorrow won't change: burn the shot.
      InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
      InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR,
      InstallReferrerClient.InstallReferrerResponse.PERMISSION_ERROR,
      -> prefs.putString(keyPrefix + TRACKED_KEY, "1")
      // SERVICE_UNAVAILABLE and unknown codes: retry on a later launch.
      else -> {}
    }
  }

  private companion object {
    const val TAG = "Appwin"
    const val TRACKED_KEY = "referrer.tracked"
    /** Server prop contract caps strings at 256 chars; truncating here keeps
     * the UTM prefix (source/medium/campaign lead the query string). */
    const val MAX_PROP_STRING = 256
  }
}
