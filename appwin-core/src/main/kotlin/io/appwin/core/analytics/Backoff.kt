package io.appwin.core.analytics

import kotlin.math.min
import kotlin.random.Random

/**
 * Exponential backoff with multiplicative jitter:
 * `min(cap, base * 2^attempt) * random(0.5..1.0)`.
 *
 * The jitter is multiplicative (not additive) so concurrent devices that
 * failed together never re-converge on the same retry instant.
 */
internal class Backoff(
  private val baseMs: Long,
  private val capMs: Long,
) {
  fun delayMs(attempt: Int, random: Random = Random.Default): Long {
    // Clamp the exponent: past the cap a bigger power changes nothing
    // and 2^attempt overflows for a long-lived retry loop.
    val exponent = min(attempt, 30)
    val raw = min(capMs.toDouble(), baseMs.toDouble() * Math.pow(2.0, exponent.toDouble()))
    return (raw * random.nextDouble(0.5, 1.0)).toLong()
  }
}
