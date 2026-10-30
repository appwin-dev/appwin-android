package io.appwin.core.consent

/**
 * What a user answered about a consent purpose.
 *
 * [GRANTED] is the default (opt-out, the PostHog model). [UNKNOWN] keeps
 * events on the device but never sends them, so an app with a consent screen
 * can initialize the SDK before showing it without losing the first session.
 * [DENIED] purges the queue and disables capture.
 */
public enum class ConsentState(internal val wire: String) {
  GRANTED("granted"),
  DENIED("denied"),
  UNKNOWN("unknown");

  internal companion object {
    fun fromWire(value: String?): ConsentState =
      entries.firstOrNull { it.wire == value } ?: UNKNOWN
  }
}
