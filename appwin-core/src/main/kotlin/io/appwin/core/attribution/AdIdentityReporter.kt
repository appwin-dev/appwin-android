package io.appwin.core.attribution

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.appwin.core.analytics.AnalyticsPrefs
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reports the device advertising identifier (GAID) to the ad-identity
 * endpoint (ADR-0038, S2S activation), strictly under the advertising
 * consent. The identifier never rides the analytics event stream: the
 * event ingest is zero-PII by contract, this is its own channel with
 * its own lifecycle - consent revoked means a DELETE, server-side row
 * gone.
 *
 * Best effort by design: a device without Play Services, a zeroed GAID
 * (limit ad tracking) or a network failure degrade to "no identifier",
 * which only lowers the match rate of the future CAPI forwarding.
 */
internal class AdIdentityReporter(
  private val context: Context,
  private val prefs: AnalyticsPrefs,
  private val keyPrefix: String,
  private val client: () -> ApiClient?,
  private val reauthorize: suspend () -> Boolean,
  private val scope: CoroutineScope,
  private val gaidProvider: suspend (Context) -> GaidInfo? = ::playServicesGaid,
) {
  internal data class GaidInfo(val id: String, val limitAdTracking: Boolean)

  private val mutex = Mutex()

  val consent: AdvertisingConsent
    get() = when (prefs.getString(keyPrefix + CONSENT_KEY)) {
      "granted" -> AdvertisingConsent.GRANTED
      "denied" -> AdvertisingConsent.DENIED
      else -> AdvertisingConsent.UNKNOWN
    }

  fun setConsent(consent: AdvertisingConsent) {
    prefs.putString(
      keyPrefix + CONSENT_KEY,
      when (consent) {
        AdvertisingConsent.GRANTED -> "granted"
        AdvertisingConsent.DENIED -> "denied"
        AdvertisingConsent.UNKNOWN -> null
      },
    )
    when (consent) {
      AdvertisingConsent.GRANTED -> sync()
      // UNKNOWN also revokes anything already sent: without a standing
      // GRANTED the server must not keep an identifier.
      AdvertisingConsent.DENIED, AdvertisingConsent.UNKNOWN -> revoke()
    }
  }

  /** Called at product start: the retry path for past failed attempts. */
  fun start() {
    if (consent == AdvertisingConsent.GRANTED) sync()
  }

  private fun sync() {
    scope.launch {
      mutex.withLock {
        if (consent != AdvertisingConsent.GRANTED) return@withLock
        val info = runCatching { gaidProvider(context) }.getOrNull()
        // No usable GAID (limited ad tracking, zeroed, no GMS) still
        // reports with a null id: the row marks the consent, so the CAPI
        // connector forwards the events - just without the identifier.
        val usable =
          info != null && !info.limitAdTracking && info.id.isNotEmpty() && info.id != ZEROED_GAID
        val marker = if (usable) info!!.id else NO_ID_MARKER
        // Same identifier is only skipped while the last report is fresh:
        // the request itself carries matching signals the server captures
        // (IP, user agent), and those go stale even when the GAID does not.
        val sentAt = prefs.getString(keyPrefix + SENT_AT_KEY)?.toLongOrNull() ?: 0L
        val fresh = System.currentTimeMillis() - sentAt < RESEND_TTL_MS
        if (prefs.getString(keyPrefix + SENT_KEY) == marker && fresh) return@withLock
        val body =
          if (usable) """{"platform":"android","adId":"${info!!.id}"}"""
          else """{"platform":"android","adId":null}"""
        if (send { it.requestVoid(ENDPOINT, HttpMethod.PUT, body) }) {
          prefs.putString(keyPrefix + SENT_KEY, marker)
          prefs.putString(keyPrefix + SENT_AT_KEY, System.currentTimeMillis().toString())
        }
      }
    }
  }

  private fun revoke() {
    scope.launch { mutex.withLock { deleteRemote() } }
  }

  private suspend fun deleteRemote() {
    if (prefs.getString(keyPrefix + SENT_KEY) == null) return
    if (send { it.requestVoid(ENDPOINT, HttpMethod.DELETE) }) {
      prefs.putString(keyPrefix + SENT_KEY, null)
    }
  }

  /** One 401-reauthorize retry, mirroring the event pipeline's policy. */
  private suspend fun send(block: suspend (ApiClient) -> Unit): Boolean {
    val api = client() ?: return false
    repeat(2) { attempt ->
      try {
        block(api)
        return true
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: AppwinApiException.Http) {
        if (error.status == 401 && attempt == 0 && reauthorize()) return@repeat
        Log.w(TAG, "attribution: ad identity report failed (${error.status})")
        return false
      } catch (error: Exception) {
        Log.w(TAG, "attribution: ad identity report failed", error)
        return false
      }
    }
    return false
  }

  private companion object {
    const val TAG = "Appwin"
    const val ENDPOINT = "/api/sdk/v1/attribution/ad-identity"
    const val CONSENT_KEY = "advertising.consent"
    const val SENT_KEY = "adid.sent"
    const val SENT_AT_KEY = "adid.sentAt"
    /** Refresh cadence of an unchanged report (matching-signal freshness). */
    const val RESEND_TTL_MS = 24L * 3_600_000
    const val ZEROED_GAID = "00000000-0000-0000-0000-000000000000"
    /** Sent-state marker for the "consent yes, identifier no" report. */
    const val NO_ID_MARKER = "-"
  }
}

/** Play Services lookup; Throwable because a GMS-less host throws Errors. */
private suspend fun playServicesGaid(context: Context): AdIdentityReporter.GaidInfo? =
  withContext(Dispatchers.IO) {
    try {
      val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
      val id = info.id ?: return@withContext null
      AdIdentityReporter.GaidInfo(id = id, limitAdTracking = info.isLimitAdTrackingEnabled)
    } catch (error: Throwable) {
      null
    }
  }
