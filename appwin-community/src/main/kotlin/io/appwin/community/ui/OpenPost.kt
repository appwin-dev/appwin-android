package io.appwin.community.ui

/**
 * The post whose detail is on screen, `null` when none is.
 *
 * What the in-app banner tests before announcing activity. Suppressing while
 * Community is mounted would silence every banner for a tabbed host; suppressing
 * while *that post* is open is the thing meant - the activity is already visible.
 *
 * Mirrors Support's `OpenThread`.
 */
internal object OpenPost {
  @Volatile
  var postId: String? = null
}
