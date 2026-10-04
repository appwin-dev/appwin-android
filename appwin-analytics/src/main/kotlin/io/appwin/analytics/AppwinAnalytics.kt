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
   *
   * Crash reporting starts with it: uncaught exceptions are written to disk
   * and uploaded on the next launch, and on Android 11+ (API 30) the ANRs
   * and native crashes of the previous runs are read from the system's
   * `ApplicationExitInfo`. An uncaught exception handler installed before
   * (Crashlytics, Sentry) keeps receiving every crash. Same consent as the
   * events.
   *
   * @param crashReporting `false` leaves crashes out entirely: no handler
   *   installed, nothing captured. [recordError] is then a no-op.
   * @param inAppPackages package prefixes of your own code when they differ
   *   from the application id (e.g. a `namespace` other than the
   *   `applicationId`). Only frames from your code group crashes into issues.
   */
  @JvmStatic
  @JvmOverloads
  public suspend fun initialize(
    crashReporting: Boolean = true,
    inAppPackages: List<String> = emptyList(),
  ): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.ANALYTICS)
    isReady = result.isReady
    if (!result.isReady) {
      reportUnavailable(AppwinProduct.ANALYTICS, result)
    } else {
      AppwinCore.startAnalytics()
      if (crashReporting) AppwinCore.startCrashReporting(inAppPackages)
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
   * Reports an error your code caught, as a non-fatal (ADR-0056): its type,
   * message and stack, plus the current screen and the last 20 screens and
   * events. Persisted right away and uploaded in the background. Never
   * throws.
   *
   * ```kotlin
   * try { sync() } catch (e: IOException) { AppwinAnalytics.recordError(e) }
   * ```
   *
   * Dropped with a log when analytics is not initialized or was initialized
   * with `crashReporting = false`; nothing is recorded under
   * [AnalyticsConsent.DENIED].
   */
  @JvmStatic
  public fun recordError(throwable: Throwable) {
    AppwinCore.analyticsRecordError(throwable)
  }

  /** For Appwin's Flutter and React Native plugins; not for app code. */
  @AppwinInternalApi
  @JvmStatic
  public fun recordBridgedError(
    runtime: String,
    fatal: Boolean,
    type: String,
    message: String?,
    frames: List<Map<String, Any?>>,
  ) {
    AppwinCore.analyticsRecordBridgedError(runtime, fatal, type, message, frames)
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
