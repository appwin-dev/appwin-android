package io.appwin.core.analytics

/**
 * Persisted GDPR consent, cached in memory. Per install on purpose: prefs
 * (not the keystore) so a reinstall starts back at the default.
 *
 * Defaults to [AnalyticsConsent.GRANTED] (opt-out, the PostHog model): most
 * studios fall under the first-party audience-measurement exemption and
 * expect analytics to work out of the box. A studio with a consent screen
 * sets [AnalyticsConsent.UNKNOWN] before it and relays the answer.
 *
 * [initial] is a consent set BEFORE `configure`: applied synchronously here,
 * so no event can slip out between the pipeline's creation and an async
 * setConsent.
 */
internal class ConsentStore(
  private val prefs: AnalyticsPrefs,
  keyPrefix: String,
  initial: AnalyticsConsent? = null,
) {
  private val key = keyPrefix + "consent"

  var consent: AnalyticsConsent =
    prefs.getString(key)?.let { AnalyticsConsent.fromWire(it) } ?: AnalyticsConsent.GRANTED
    private set

  init {
    if (initial != null && initial != consent) set(initial)
  }

  fun set(newValue: AnalyticsConsent) {
    consent = newValue
    prefs.putString(key, newValue.wire)
  }
}
