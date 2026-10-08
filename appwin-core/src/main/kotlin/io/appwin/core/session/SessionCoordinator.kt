package io.appwin.core.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises `/auth/init` calls.
 *
 * Concurrent callers asking for the same identity share **one** network call.
 * Not an optimisation: `/auth/init` rotates the token and invalidates the
 * previous one, so two simultaneous inits revoke each other's token, which
 * shows up as intermittent 401s at startup.
 *
 * Sharing is keyed by `externalId`: a caller that wants a different identity
 * than the call in flight (an `identify` racing a background anonymous
 * bootstrap) waits for it, then opens its own. Handing it the in-flight
 * session would silently leave the user anonymous.
 */
internal class SessionCoordinator(
  private val scope: CoroutineScope,
  private val open: suspend (externalId: String?) -> String,
) {
  private class InFlight(val externalId: String?, val task: Deferred<String>)

  private val mutex = Mutex()
  private var inFlight: InFlight? = null

  suspend fun bootstrap(externalId: String?): String {
    while (true) {
      val current = mutex.withLock {
        inFlight ?: InFlight(externalId, scope.async { open(externalId) }).also { inFlight = it }
      }
      val matches = current.externalId == externalId
      val result = try {
        runCatching { current.task.await() }
      } finally {
        mutex.withLock { if (inFlight === current) inFlight = null }
      }
      if (matches) return result.getOrThrow()
      // Another identity's call, successful or not: ours still has to go out.
    }
  }

  fun reset() {
    inFlight = null
  }
}
