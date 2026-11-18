package io.appwin.community.events

import io.appwin.community.AppwinCommunityEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppwinCommunityEventsTest {
  @Test
  fun `every collector receives the events emitted while it collects`() = runTest(UnconfinedTestDispatcher()) {
    val first = async(start = CoroutineStart.UNDISPATCHED) { AppwinCommunityEvents.events.take(2).toList() }
    val second = async(start = CoroutineStart.UNDISPATCHED) { AppwinCommunityEvents.events.take(2).toList() }

    AppwinCommunityEvents.emit(AppwinCommunityEvent.PostCreated("p1"))
    AppwinCommunityEvents.emit(AppwinCommunityEvent.ReactionModified("p1", "c1", null))

    val expected = listOf(
      AppwinCommunityEvent.PostCreated("p1"),
      AppwinCommunityEvent.ReactionModified("p1", "c1", null),
    )
    assertEquals(expected, first.await())
    assertEquals(expected, second.await())
  }

  @Test
  fun `emitting without a collector neither blocks nor replays later`() = runTest(UnconfinedTestDispatcher()) {
    repeat(200) { AppwinCommunityEvents.emit(AppwinCommunityEvent.PostCreated("old-$it")) }
    val next = async(start = CoroutineStart.UNDISPATCHED) { AppwinCommunityEvents.events.take(1).toList() }
    AppwinCommunityEvents.emit(AppwinCommunityEvent.ProfileUpdated("me"))
    assertEquals(listOf(AppwinCommunityEvent.ProfileUpdated("me")), next.await())
  }
}
