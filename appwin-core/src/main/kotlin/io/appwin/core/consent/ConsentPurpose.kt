package io.appwin.core.consent

/**
 * What an event may be used for. Each event leaves the device stamped with
 * the purposes granted at that moment.
 */
public enum class ConsentPurpose(internal val wire: String) {
  ANALYTICS("analytics"),
  MARKETING("marketing"),
  ADVERTISING("advertising"),
}
