package io.appwin.community.events

import io.appwin.community.AppwinCommunityEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The one emitter behind [io.appwin.community.AppwinCommunity.events]. */
internal object AppwinCommunityEvents {
  // Never suspends the success path of a UI action: a slow host collector
  // loses the oldest events rather than stalling the feed.
  private val _events = MutableSharedFlow<AppwinCommunityEvent>(
    replay = 0,
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  val events: SharedFlow<AppwinCommunityEvent> = _events.asSharedFlow()

  fun emit(event: AppwinCommunityEvent) {
    _events.tryEmit(event)
  }
}
