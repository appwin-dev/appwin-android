package io.appwin.notifications

/**
 * Whether a permission result is news for Core. Only a grant that turned
 * notifications on counts: a user who already had them on (an upgrade from a
 * version that never reported `push_opt_in`) tapping the CTA is not a new
 * opt-in, and signalling it would let Core announce them as one.
 */
internal object PushPermissionGrant {
  fun shouldSignalCore(enabledBeforeRequest: Boolean, granted: Boolean): Boolean =
    granted && !enabledBeforeRequest
}
