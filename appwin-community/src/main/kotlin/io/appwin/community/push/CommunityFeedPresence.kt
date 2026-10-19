package io.appwin.community.push

import android.os.Handler
import android.os.Looper
import io.appwin.community.AppwinCommunityPostTarget
import java.util.concurrent.CopyOnWriteArrayList

/** Feeds currently composed, most recent last. */
internal object CommunityFeedPresence {
  class Feed(
    /** Evaluated when a target arrives: a tab the member left is still mounted. */
    val isVisible: () -> Boolean,
    val open: (AppwinCommunityPostTarget) -> Unit,
  )

  private class Pending(val target: AppwinCommunityPostTarget, val onTimeout: () -> Unit) {
    var cancel: () -> Unit = {}
  }

  private val feeds = CopyOnWriteArrayList<Feed>()
  private val lock = Any()
  private var pending: Pending? = null

  /** Runs `action` after `delayMs` and returns its cancellation. Swapped by tests. */
  internal var scheduler: (delayMs: Long, action: () -> Unit) -> (() -> Unit) = { delayMs, action ->
    val handler = Handler(Looper.getMainLooper())
    val runnable = Runnable(action)
    handler.postDelayed(runnable, delayMs)
    ({ handler.removeCallbacks(runnable) })
  }

  fun register(feed: Feed) {
    feeds.add(feed)
    val claimed = synchronized(lock) { pending.also { pending = null } } ?: return
    claimed.cancel()
    feed.open(claimed.target)
  }

  /**
   * Keeps [target] for the first feed that registers within [graceMs], else
   * calls [onTimeout]. Compose builds tabs lazily: a host that switches to a
   * Community tab never shown before calls `openPost` before that feed exists.
   * A newer target replaces an older one, whose fallback is dropped.
   */
  fun awaitFeed(target: AppwinCommunityPostTarget, graceMs: Long, onTimeout: () -> Unit) {
    val entry = Pending(target, onTimeout)
    val previous = synchronized(lock) { pending.also { pending = entry } }
    previous?.cancel?.invoke()
    entry.cancel = scheduler(graceMs) {
      val expired = synchronized(lock) { (pending === entry).also { if (it) pending = null } }
      if (expired) entry.onTimeout()
    }
  }

  fun unregister(feed: Feed) {
    feeds.remove(feed)
  }

  fun visible(): Feed? = feeds.lastOrNull { it.isVisible() }

  fun mounted(): Feed? = visible() ?: feeds.lastOrNull()
}

internal enum class CommunityOpenRoute { HOST, FEED, AWAIT_FEED, MODAL }

/**
 * Where a post target goes.
 *
 * A tap only reuses a feed the member can see: a feed parked in another tab
 * would open the post out of sight. `openPost` is called by a host that is
 * switching to its Community tab, so any mounted feed will do, and one about
 * to mount is worth a short wait.
 */
internal fun communityOpenRoute(
  fromTap: Boolean,
  hostHandlesTaps: Boolean,
  hasVisibleFeed: Boolean,
  hasMountedFeed: Boolean,
): CommunityOpenRoute = when {
  fromTap && hostHandlesTaps -> CommunityOpenRoute.HOST
  hasVisibleFeed -> CommunityOpenRoute.FEED
  !fromTap && hasMountedFeed -> CommunityOpenRoute.FEED
  !fromTap -> CommunityOpenRoute.AWAIT_FEED
  else -> CommunityOpenRoute.MODAL
}
