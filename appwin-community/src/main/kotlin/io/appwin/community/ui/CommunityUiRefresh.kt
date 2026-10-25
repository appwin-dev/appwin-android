package io.appwin.community.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Asks any mounted [CommunityViewModel] to re-bootstrap after identity changes.
 *
 * Hosts call `AppwinCore.identify` or `logout` after the feed may already be on
 * screen with the previous profile; without this pulse the header avatar stays
 * stale.
 */
internal object CommunityUiRefresh {
  private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val requests: SharedFlow<Unit> = _requests.asSharedFlow()

  fun request() {
    _requests.tryEmit(Unit)
  }

  private val _countRequests = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /**
   * Lighter pulse: re-read the bootstrap counts (the sanctions bell, unread
   * activity) without touching the feed. Sent when a community notification
   * lands, sanctions included, whether or not a banner is shown for it.
   */
  val countRequests: SharedFlow<Unit> = _countRequests.asSharedFlow()

  fun requestCounts() {
    _countRequests.tryEmit(Unit)
  }
}
