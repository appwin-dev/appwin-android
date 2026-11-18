package io.appwin.core.analytics

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {

  // random(0.5..1.0) fixed at its upper bound to observe the raw curve.
  private class MaxRandom : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextDouble(from: Double, until: Double): Double = until
  }

  @Test
  fun `la suite des delais double jusqu'au plafond`() {
    val backoff = Backoff(baseMs = 2_000, capMs = 300_000)
    val random = MaxRandom()
    assertEquals(2_000L, backoff.delayMs(0, random))
    assertEquals(4_000L, backoff.delayMs(1, random))
    assertEquals(8_000L, backoff.delayMs(2, random))
    assertEquals(256_000L, backoff.delayMs(7, random))
    assertEquals(300_000L, backoff.delayMs(8, random))
    assertEquals(300_000L, backoff.delayMs(50, random))
  }

  @Test
  fun `le jitter reste dans la moitie superieure du delai brut`() {
    val backoff = Backoff(baseMs = 2_000, capMs = 300_000)
    repeat(1_000) {
      val delay = backoff.delayMs(3, Random.Default)
      assertTrue("delay=$delay", delay in 8_000..16_000)
    }
  }

  @Test
  fun `un grand nombre de tentatives ne deborde pas`() {
    val backoff = Backoff(baseMs = 2_000, capMs = 300_000)
    val delay = backoff.delayMs(Int.MAX_VALUE, MaxRandom())
    assertEquals(300_000L, delay)
  }
}
