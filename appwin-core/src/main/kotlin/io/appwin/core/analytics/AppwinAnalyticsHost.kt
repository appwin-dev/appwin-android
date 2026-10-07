package io.appwin.core.analytics

import android.app.Activity
import io.appwin.core.AppForeground
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.inapp.AppwinInAppBanner
import io.appwin.core.network.MultipartFile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * The parts of the analytics runtime that the session replay recorder needs
 * (ADR-0057): the recorder lives in the Analytics module so an app without it
 * ships no capture code, and Core keeps the session, consent and transport.
 */
@AppwinInternalApi
public object AppwinAnalyticsHost {
  /** Current analytics session, null before the first foreground and after DENIED. */
  public val sessionId: String?
    get() = AppwinCore.analytics.currentSessionId

  public val consent: AnalyticsConsent
    get() = AppwinCore.analytics.consent

  public val inForeground: StateFlow<Boolean>
    get() = AppForeground.inForeground

  /** Main thread only. Null between a pause and the next resume. */
  public val resumedActivity: Activity?
    get() = AppwinInAppBanner.resumedActivity

  /** Settings of [product] in the last known availability verdict. */
  public fun productConfig(product: AppwinProduct): JsonObject? =
    AppwinCore.availabilityStore?.cachedConfig(product)

  /** [Backoff.INGEST], the curve every SDK upload backs off on. */
  public fun retryDelayMs(attempt: Int): Long = Backoff.INGEST.delayMs(attempt)

  /** After [SendOutcome.UNAUTHORIZED]: mints a fresh bearer, false when it could not. */
  public suspend fun reauthorize(): Boolean = AppwinCore.reauthorize()

  /** Authenticated multipart POST, read with the retry policy of every SDK upload. */
  public suspend fun postMultipart(
    path: String,
    fields: Map<String, String>,
    file: MultipartFile,
  ): SendOutcome {
    val client = AppwinCore.client ?: return SendOutcome.RETRYABLE
    return sendOutcome(label = path) {
      client.requestMultipart(path, fields, file)
      SendOutcome.OK
    }
  }
}
