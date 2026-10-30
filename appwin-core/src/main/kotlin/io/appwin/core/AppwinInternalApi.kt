package io.appwin.core

/**
 * Marks a Core API that exists only for the other Appwin modules. Kotlin's
 * `internal` stops at the module boundary, and the product façades live in
 * their own modules; this opt-in is the boundary a studio must not cross -
 * these members are not a contract and can change without notice.
 */
@RequiresOptIn(
  message = "Internal Appwin SDK API: reserved for Appwin product modules, subject to change.",
  level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
public annotation class AppwinInternalApi
