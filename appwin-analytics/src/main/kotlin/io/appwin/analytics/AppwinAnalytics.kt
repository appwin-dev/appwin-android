@file:OptIn(AppwinInternalApi::class)

package io.appwin.analytics

import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.reportUnavailable

/** Re-exported so a studio using analytics never imports an `io.appwin.core.*` type by hand. */
public typealias AnalyticsConsent = io.appwin.core.analytics.AnalyticsConsent

/**
 * Appwin Analytics: behavioral events for the dashboards, funnels and
 * experiments of the Appwin studio.
 *
 * The heavy lifting - persisted queue, batching, offline buffering, session
 * tracking, GDPR consent - lives in `appwin-core` and starts with
 * [AppwinCore.configure]. This façade is the product's public surface, like
 * `AppwinSupport` or `AppwinNotifications` for theirs.
 *
 * ```kotlin
 * AppwinCore.configure(this, projectAppId = "…")
 * AppwinAnalytics.setConsent(AnalyticsConsent.GRANTED)
 * AppwinAnalytics.screen("home")
 * AppwinAnalytics.track("purchase", mapOf("plan" to "pro", "seats" to 3))
 * ```
 */
public object AppwinAnalytics {

  /**
   * Starts the event pipeline, availability permitting. Call it once after
   * [AppwinCore.configure], as early as you want the capture to begin -
   * sessions and installs are only recorded from this point on.
   *
   * Same contract as the other products: the server verdict (plan, product
   * toggle) gates the start, the result is cached on disk so an offline
   * launch falls back to the last known answer, and repeated calls are
   * cheap.
   */
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.ANALYTICS)
    isReady = result.isReady
    if (!result.isReady) {
      reportUnavailable(AppwinProduct.ANALYTICS, result)
    } else {
      AppwinCore.startAnalytics()
    }
    return result
  }

  /** Whether [initialize] has returned [AppwinInitResult.Ready]. */
  @JvmStatic
  public var isReady: Boolean = false
    private set

  /**
   * Queues a custom analytics event. Never blocks and never throws: the
   * event is persisted locally and uploaded in batches (offline included).
   *
   * [name] must match `^[a-z][a-z0-9_]{0,63}$` and not shadow a reserved
   * name (`session_start`, `session_end`, `screen_view`, `app_install`,
   * `app_update`); invalid events are dropped with a log. [props] values
   * must be String, Boolean or numbers (anything else is dropped); props
   * are capped at 20 keys and string values truncated to 256 characters.
   */
  @JvmStatic
  @JvmOverloads
  public fun track(name: String, props: Map<String, Any?>? = null) {
    AppwinCore.analyticsTrack(name, props)
  }

  /**
   * Emits the reserved `screen_view` event for [name] (truncated to 128
   * characters). Screen names feed funnel steps and breakdowns.
   */
  @JvmStatic
  public fun screen(name: String) {
    AppwinCore.analyticsScreen(name)
  }

  /**
   * Forces an immediate upload of the pending queue. Rarely needed: the
   * pipeline already flushes on volume, on a timer, and when the app goes
   * to the background.
   */
  @JvmStatic
  public fun flush() {
    AppwinCore.analyticsFlush()
  }

  /**
   * Sets the GDPR consent for analytics. The default is
   * [AnalyticsConsent.GRANTED] (opt-out): most studios fall under the
   * first-party audience-measurement exemption and need no consent screen.
   * If yours has one, call `setConsent(UNKNOWN)` at launch (before
   * `configure` works too) - events are then captured and persisted but
   * never uploaded - and relay the user's answer: GRANTED uploads the
   * backlog, DENIED purges it and mutes capture.
   */
  @JvmStatic
  public fun setConsent(consent: AnalyticsConsent) {
    AppwinCore.analyticsSetConsent(consent)
  }
}
