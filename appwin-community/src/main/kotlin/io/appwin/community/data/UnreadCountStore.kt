package io.appwin.community.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unread notification count shared by every badge.
 *
 * Fed for free by the bootstraps the SDK already does; the refreshes it runs on
 * its own (push, foreground) are throttled and skipped while nobody listens, so
 * a badge never turns into background polling.
 */
internal class UnreadCountStore(
  private val scope: CoroutineScope,
  private val fetch: suspend () -> Int,
  private val clock: () -> Long = System::currentTimeMillis,
  private val minRefreshIntervalMs: Long = 30_000L,
) {
  private val count = MutableStateFlow<Int?>(null)
  private val inFlight = AtomicBoolean(false)

  @Volatile
  private var lastRefreshAt: Long? = null

  val updates: Flow<Int> =
    count
      // The collector is not subscribed yet when onStart runs, so the refresh
      // is forced rather than gated on the subscription count.
      .onStart { refresh(requireSubscriber = false) }
      .filterNotNull()
      .distinctUntilChanged()

  val current: Int?
    get() = count.value

  /** A value read from the network by someone else (bootstrap). */
  fun update(value: Int) {
    lastRefreshAt = clock()
    count.value = value
  }

  /** Something may have changed the count (push, tap, foreground). */
  fun refresh(requireSubscriber: Boolean = true) {
    if (requireSubscriber && count.subscriptionCount.value == 0) return
    val last = lastRefreshAt
    if (last != null && clock() - last < minRefreshIntervalMs) return
    if (!inFlight.compareAndSet(false, true)) return
    lastRefreshAt = clock()
    scope.launch {
      try {
        runCatching { fetch() }.onSuccess { count.value = it }
      } finally {
        inFlight.set(false)
      }
    }
  }
}
