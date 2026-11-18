package io.appwin.analytics.replay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPacerTest {
  private val start = 10_000L

  private fun isDue(after: Long, touching: Boolean = false, touchedAgo: Long? = null) =
    ReplayPacer.isDue(start + after, start, touching, touchedAgo?.let { start + after - it } ?: 0L)

  @Test
  fun `one frame per second when nothing moves`() {
    assertTrue(ReplayPacer.isDue(start, 0L, touching = false, lastTouchAt = 0L))
    assertFalse(isDue(500))
    assertTrue(isDue(1_000))
  }

  @Test
  fun `waits while touched then for the settle`() {
    assertFalse(isDue(2_000, touching = true))
    assertFalse(isDue(2_000, touchedAgo = 500))
    assertTrue(isDue(2_000, touchedAgo = 1_000))
  }

  @Test
  fun `never waits past the max gap`() {
    assertTrue(isDue(ReplayPacer.MAX_GAP_MS, touching = true))
  }

  @Test
  fun `waits until the window has stopped drawing`() {
    val now = start + 2_000
    assertFalse(ReplayPacer.isDue(now, start, touching = false, lastTouchAt = 0L, lastDrawAt = now - 16))
    assertTrue(ReplayPacer.isDue(now, start, touching = false, lastTouchAt = 0L, lastDrawAt = now - ReplayPacer.STILL_MS))
  }
}
