package io.appwin.core.analytics

/**
 * GDPR consent state for the analytics pipeline.
 *
 * [GRANTED] is the default (opt-out, the PostHog model). [UNKNOWN] queues
 * events on disk but never sends them, so an app with a consent screen can
 * initialize the SDK before showing it without losing the first session.
 * [DENIED] purges the queue and disables capture.
 */
public enum class AnalyticsConsent(internal val wire: String) {
  GRANTED("granted"),
  DENIED("denied"),
  UNKNOWN("unknown");

  internal companion object {
    fun fromWire(value: String?): AnalyticsConsent =
      entries.firstOrNull { it.wire == value } ?: UNKNOWN
  }
}
