package io.appwin.core.session

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingConfig
import com.android.billingclient.api.BillingConfigResponseListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.PendingPurchasesParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * The Play Store account's country, sent on `/sdk/v1/auth/init` as
 * `storeCountry`: the country Google Play (and RevenueCat) report purchases in,
 * which neither the SIM nor the locale is.
 *
 * Only Play Billing knows it (`BillingClient.getBillingConfigAsync`). Core does
 * not ship Play Billing (compileOnly): the country is read when the host app
 * already embeds it, and is null otherwise. Never a SIM or locale country in
 * its place.
 */
internal object StoreCountry {
  private const val TAG = "Appwin"
  private const val BILLING_CLIENT_CLASS = "com.android.billingclient.api.BillingClient"

  /** Longest wait on a session init; past it, the init goes without. */
  internal const val TIMEOUT_MS = 1_000L

  /** The account's country only changes on an account switch: read once per process. */
  @Volatile private var cached: String? = null

  /** Test seam: replaces the Play Billing read. */
  @Volatile internal var reader: (suspend (Context) -> String?)? = null

  suspend fun current(context: Context?): String? {
    cached?.let { return it }
    val context = context ?: return null
    val read = reader ?: { if (billingAvailable()) PlayBillingCountry.read(it) else null }
    val country = runCatching { withTimeoutOrNull(TIMEOUT_MS) { read(context) } }
      .getOrNull()
      ?.let(::normalized)
    if (country != null) cached = country
    return country
  }

  /** Keeps a two or three letter code, upper-cased; anything else is null. */
  internal fun normalized(code: String?): String? {
    val trimmed = code?.trim()?.uppercase() ?: return null
    if (trimmed.length !in 2..3) return null
    if (!trimmed.all { it in 'A'..'Z' }) return null
    return trimmed
  }

  internal fun resetForTesting() {
    cached = null
    reader = null
  }

  private fun billingAvailable(): Boolean =
    runCatching { Class.forName(BILLING_CLIENT_CLASS, false, StoreCountry::class.java.classLoader) }
      .isSuccess
}

/**
 * Every Play Billing reference lives here, apart from [StoreCountry]: this
 * class is only loaded once the host app is known to ship Play Billing, so
 * its verification never meets a missing class.
 */
private object PlayBillingCountry {
  private const val TAG = "Appwin"

  /**
   * Opens a short-lived BillingClient and asks its billing config. Any
   * failure (no Play Billing in the app, an older Play Billing without the
   * call, no Play Store) yields null: the init must never fail for this.
   */
  suspend fun read(context: Context): String? {
    val client = try {
      newClient(context.applicationContext)
    } catch (t: Throwable) {
      Log.w(TAG, "store country: Play Billing unavailable", t)
      return null
    }
    return try {
      suspendCancellableCoroutine { continuation ->
        val done = AtomicBoolean(false)
        fun finish(value: String?) {
          if (done.compareAndSet(false, true)) continuation.resume(value)
        }
        continuation.invokeOnCancellation { done.set(true) }
        try {
          client.startConnection(
            object : BillingClientStateListener {
              override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                  finish(null)
                  return
                }
                try {
                  client.getBillingConfigAsync(
                    GetBillingConfigParams.newBuilder().build(),
                    object : BillingConfigResponseListener {
                      override fun onBillingConfigResponse(
                        result: BillingResult,
                        config: BillingConfig?,
                      ) {
                        val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                        finish(if (ok) config?.countryCode else null)
                      }
                    },
                  )
                } catch (t: Throwable) {
                  // LinkageError on a Play Billing older than 5.2.
                  finish(null)
                }
              }

              override fun onBillingServiceDisconnected() = finish(null)
            },
          )
        } catch (t: Throwable) {
          finish(null)
        }
      }
    } finally {
      runCatching { client.endConnection() }
    }
  }

  /**
   * Play Billing 7+ wants `PendingPurchasesParams` (8 dropped the no-argument
   * call); 5 and 6 only know the no-argument one.
   */
  private fun newClient(context: Context): BillingClient {
    val builder = BillingClient.newBuilder(context).setListener { _, _ -> }
    try {
      builder.enablePendingPurchases(
        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
      )
    } catch (_: LinkageError) {
      @Suppress("DEPRECATION")
      builder.enablePendingPurchases()
    }
    return builder.build()
  }
}
