package io.appwin.community.ui

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
}
