package io.appwin.core.push

import io.appwin.core.analytics.AnalyticsPrefs

/**
 * The "push opt-in already reported" mark and the decision to report.
 *
 * `push_opt_in` leaves once per installation, and only for an opt-in the SDK
 * witnessed: a fresh install, or a grant seen in this process. An app that
 * updates with users already opted in must not announce them all as new
 * opt-ins (a journey triggered on `push_opt_in` would fire for the whole user
 * base): for them the mark is set silently.
 */
internal object PushOptInFlag {
  private const val KEY_SUFFIX = "pushOptIn.tracked"

  fun shouldEmit(freshInstall: Boolean, grantedThisProcess: Boolean, alreadyClaimed: Boolean): Boolean =
    !alreadyClaimed && (freshInstall || grantedThisProcess)

  /** Sets the mark if it was not; true when the event must be emitted. */
  fun claim(
    prefs: AnalyticsPrefs,
    keyPrefix: String,
    freshInstall: Boolean,
    grantedThisProcess: Boolean,
  ): Boolean {
    val key = keyPrefix + KEY_SUFFIX
    val alreadyClaimed = prefs.getString(key) != null
    if (!alreadyClaimed) prefs.putString(key, "1")
    return shouldEmit(freshInstall, grantedThisProcess, alreadyClaimed)
  }

  /**
   * Decides and reports in one step. Nothing is claimed while the pipeline
   * does not exist: the flag would be set for an event that was then dropped,
   * and the opt-in lost for good. [emit] hands the event to the pipeline.
   */
  fun report(
    prefs: AnalyticsPrefs,
    keyPrefix: String,
    pipelineReady: Boolean,
    freshInstall: Boolean,
    grantedThisProcess: Boolean,
    emit: () -> Unit,
  ) {
    if (!pipelineReady) return
    if (claim(prefs, keyPrefix, freshInstall, grantedThisProcess)) emit()
  }
}
