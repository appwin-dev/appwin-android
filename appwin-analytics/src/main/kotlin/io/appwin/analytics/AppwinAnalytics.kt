@file:OptIn(AppwinInternalApi::class)

package io.appwin.analytics

import io.appwin.analytics.replay.ReplayController
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.reportUnavailable
import io.appwin.core.consent.ConsentState

/** Kept so existing code compiles; the type is Core's [ConsentState]. */
@Deprecated("Use io.appwin.core.consent.ConsentState", ReplaceWith("ConsentState"))
public typealias AnalyticsConsent = ConsentState

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
 * AppwinCore.setConsent(ConsentState.GRANTED)
 * AppwinAnalytics.screen("home")
 * AppwinCore.track("purchase", mapOf("plan" to "pro", "seats" to 3))
 * ```
 */
public object AppwinAnalytics {

  /**
   * Starts the Analytics product, availability permitting. Call it once
   * after [AppwinCore.configure]. The event pipeline itself (events,
   * sessions, installs) already runs from [AppwinCore.configure], whichever
   * products are installed; this call adds crash reporting and session
   * replay.
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
   * Session replay starts with it too, once the studio has switched it on
   * for the app in the dashboard (it is off by default): see [AppwinReplay].
   * Same consent again: only GRANTED records.
   *
   * @param crashReporting `false` leaves crashes out entirely: no handler
   *   installed, nothing captured. [recordError] is then a no-op.
   * @param inAppPackages package prefixes of your own code when they differ
   *   from the application id (e.g. a `namespace` other than the
   *   `applicationId`). Only frames from your code group crashes into issues.
   * @param sessionReplay `false` never records this app's sessions, whatever
   *   the dashboard says, and deletes anything an earlier build recorded.
   */
  @JvmStatic
  @JvmOverloads
  public suspend fun initialize(
    crashReporting: Boolean = true,
    inAppPackages: List<String> = emptyList(),
    sessionReplay: Boolean = true,
  ): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.ANALYTICS)
    isReady = result.isReady
    if (!result.isReady) {
      reportUnavailable(AppwinProduct.ANALYTICS, result)
    } else {
      AppwinCore.startAnalytics()
      if (crashReporting) AppwinCore.startCrashReporting(inAppPackages)
      if (sessionReplay) ReplayController.start() else ReplayController.optOut()
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
  @Deprecated("Use AppwinCore.track", ReplaceWith("AppwinCore.track(name, props)", "io.appwin.core.AppwinCore"))
  @JvmStatic
  @JvmOverloads
  public fun track(name: String, props: Map<String, Any?>? = null) {
    AppwinCore.track(name, props)
  }

  /**
   * Emits the reserved `screen_view` event for [name] (truncated to 128
   * characters). Screen names feed funnel steps and breakdowns.
   */
  @JvmStatic
  public fun screen(name: String) {
    AppwinCore.analyticsScreen(name)
    ReplayController.onScreen(name.take(MAX_SCREEN_LENGTH))
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
   * [ConsentState.DENIED].
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
   * [ConsentState.GRANTED] (opt-out): most studios fall under the
   * first-party audience-measurement exemption and need no consent screen.
   * If yours has one, call `setConsent(UNKNOWN)` at launch (before
   * `configure` works too) - events are then captured and persisted but
   * never uploaded - and relay the user's answer: GRANTED uploads the
   * backlog, DENIED purges it and mutes capture.
   */
  @Deprecated("Use AppwinCore.setConsent", ReplaceWith("AppwinCore.setConsent(consent)", "io.appwin.core.AppwinCore"))
  @JvmStatic
  public fun setConsent(consent: ConsentState) {
    // Core tells the replay recorder (ReplayController.listenToConsent).
    AppwinCore.setConsent(consent)
  }

  private const val MAX_SCREEN_LENGTH = 128
}
