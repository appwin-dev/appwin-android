package io.appwin.community.push

import io.appwin.community.AppwinCommunityPostTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityOpenRouteTest {
  @Test
  fun `a tap goes to the host when it handles taps, whatever is on screen`() {
    assertEquals(CommunityOpenRoute.HOST, communityOpenRoute(true, true, true, true))
    assertEquals(CommunityOpenRoute.HOST, communityOpenRoute(true, true, false, false))
  }

  @Test
  fun `a tap reuses only a visible feed`() {
    assertEquals(CommunityOpenRoute.FEED, communityOpenRoute(true, false, true, true))
    assertEquals(CommunityOpenRoute.MODAL, communityOpenRoute(true, false, false, true))
    assertEquals(CommunityOpenRoute.MODAL, communityOpenRoute(true, false, false, false))
  }

  @Test
  fun `openPost reuses any mounted feed and never calls the host back`() {
    assertEquals(CommunityOpenRoute.FEED, communityOpenRoute(false, true, false, true))
    assertEquals(CommunityOpenRoute.FEED, communityOpenRoute(false, false, true, true))
    assertEquals(CommunityOpenRoute.AWAIT_FEED, communityOpenRoute(false, true, false, false))
    assertEquals(CommunityOpenRoute.AWAIT_FEED, communityOpenRoute(false, false, false, false))
  }

  @Test
  fun `the presence registry prefers the most recent visible feed`() {
    val hidden = CommunityFeedPresence.Feed(isVisible = { false }, open = {})
    val visible = CommunityFeedPresence.Feed(isVisible = { true }, open = {})
    val newerHidden = CommunityFeedPresence.Feed(isVisible = { false }, open = {})
    listOf(hidden, visible, newerHidden).forEach(CommunityFeedPresence::register)
    try {
      assertEquals(visible, CommunityFeedPresence.visible())
      assertEquals(visible, CommunityFeedPresence.mounted())
      CommunityFeedPresence.unregister(visible)
      assertEquals(null, CommunityFeedPresence.visible())
      assertEquals(newerHidden, CommunityFeedPresence.mounted())
    } finally {
      listOf(hidden, visible, newerHidden).forEach(CommunityFeedPresence::unregister)
    }
  }

  private val timers = mutableListOf<Pair<Long, () -> Unit>>()
  private val cancelled = mutableSetOf<Int>()

  private fun fakeScheduler() {
    CommunityFeedPresence.scheduler = { delay, action ->
      val index = timers.size
      timers += delay to action
      ({ cancelled += index })
    }
  }

  private fun fireTimers() {
    timers.forEachIndexed { index, (_, action) -> if (index !in cancelled) action() }
  }

  @After
  fun resetScheduler() {
    timers.forEachIndexed { index, _ -> cancelled += index }
  }

  @Test
  fun `a feed that mounts within the grace window takes the pending target`() {
    fakeScheduler()
    val opened = mutableListOf<AppwinCommunityPostTarget>()
    var modal = 0
    CommunityFeedPresence.awaitFeed(AppwinCommunityPostTarget("p1", "c1"), 600) { modal++ }
    assertEquals(600L, timers.single().first)

    val feed = CommunityFeedPresence.Feed(isVisible = { true }, open = { opened += it })
    CommunityFeedPresence.register(feed)
    try {
      fireTimers()
      assertEquals(listOf(AppwinCommunityPostTarget("p1", "c1")), opened)
      assertEquals(0, modal)
    } finally {
      CommunityFeedPresence.unregister(feed)
    }
  }

  @Test
  fun `without a feed the modal opens once the window lapses`() {
    fakeScheduler()
    var modal = 0
    CommunityFeedPresence.awaitFeed(AppwinCommunityPostTarget("p1"), 600) { modal++ }
    fireTimers()
    assertEquals(1, modal)

    // Consumed: a feed mounting afterwards gets nothing.
    val opened = mutableListOf<AppwinCommunityPostTarget>()
    val feed = CommunityFeedPresence.Feed(isVisible = { true }, open = { opened += it })
    CommunityFeedPresence.register(feed)
    CommunityFeedPresence.unregister(feed)
    assertTrue(opened.isEmpty())
  }

  @Test
  fun `a newer target replaces the pending one`() {
    fakeScheduler()
    var firstModal = 0
    var secondModal = 0
    CommunityFeedPresence.awaitFeed(AppwinCommunityPostTarget("p1"), 600) { firstModal++ }
    CommunityFeedPresence.awaitFeed(AppwinCommunityPostTarget("p2"), 600) { secondModal++ }
    fireTimers()
    assertEquals(0, firstModal)
    assertEquals(1, secondModal)
  }
}
