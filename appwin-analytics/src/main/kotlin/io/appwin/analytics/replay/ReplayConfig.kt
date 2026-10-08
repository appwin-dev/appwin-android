package io.appwin.analytics.replay

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** `products.replay.config` of the availability verdict (`ReplayConfigSchema`). */
internal data class ReplayConfig(
  val sampleRate: Double = 1.0,
  val maskAllText: Boolean = true,
  val maskAllImages: Boolean = true,
) {
  companion object {
    /** A missing or malformed field keeps its default: masking must never fail open. */
    fun from(json: JsonObject?): ReplayConfig {
      json ?: return ReplayConfig()
      val defaults = ReplayConfig()
      return ReplayConfig(
        sampleRate = (json["sampleRate"] as? JsonPrimitive)?.doubleOrNull
          ?.coerceIn(0.0, 1.0) ?: defaults.sampleRate,
        maskAllText = (json["maskAllText"] as? JsonPrimitive)?.booleanOrNull ?: defaults.maskAllText,
        maskAllImages = (json["maskAllImages"] as? JsonPrimitive)?.booleanOrNull
          ?: defaults.maskAllImages,
      )
    }
  }
}

/** Limits shared by every SDK (`REPLAY_LIMITS` in the contracts). */
internal object ReplayLimits {
  const val SEGMENT_MS = 10_000L
  const val FRAME_INTERVAL_MS = 1_000L
  const val CAPTURE_SCALE = 2f
  const val VIDEO_BITRATE = 400_000
  const val MAX_SESSION_FRAMES = 3_600
  const val SEGMENT_MAX_BYTES = 2L * 1024 * 1024
  const val QUEUE_MAX_BYTES = 20L * 1024 * 1024
  const val MAX_TOUCHES = 1_000
  const val MAX_SCREENS = 100
}

/**
 * When the next frame is taken. A capture costs the main thread about 12 ms
 * (Galaxy S22) and its window copy runs on the render thread: a stutter on
 * anything moving, nothing on a still screen. So a due frame waits while a
 * finger is on the screen, for [SETTLE_MS] after the last touch, and until
 * the window has not drawn for [STILL_MS] (an animation, a scroll or a
 * transition draws every frame). Past [MAX_GAP_MS] since the previous frame
 * it is taken anyway: a screen that never stops moving would otherwise never
 * be recorded. Same rule as iOS.
 */
internal object ReplayPacer {
  const val POLL_MS = 250L
  const val SETTLE_MS = 1_000L
  const val STILL_MS = 250L
  const val MAX_GAP_MS = 10_000L

  /** [lastFrameAt], [lastTouchAt] and [lastDrawAt] are 0 when there was none. */
  fun isDue(
    now: Long,
    lastFrameAt: Long,
    touching: Boolean,
    lastTouchAt: Long,
    lastDrawAt: Long = 0L,
  ): Boolean {
    if (lastFrameAt == 0L) return true
    val elapsed = now - lastFrameAt
    // Half a poll of slack: the ticks land on a 250 ms grid, a strict
    // comparison would push every frame one poll late.
    if (elapsed < ReplayLimits.FRAME_INTERVAL_MS - POLL_MS / 2) return false
    if (elapsed >= MAX_GAP_MS) return true
    if (touching) return false
    if (lastDrawAt != 0L && now - lastDrawAt < STILL_MS) return false
    return lastTouchAt == 0L || now - lastTouchAt >= SETTLE_MS
  }
}

internal object ReplaySampling {
  /**
   * Same decision on every platform and every tab: the last 8 hex digits of
   * the session id as a uint32, over 2^32. Not the first: a UUIDv7 starts
   * with its timestamp, which would give every session the same value.
   */
  fun isSampled(sessionId: String, sampleRate: Double): Boolean {
    if (sampleRate >= 1.0) return true
    if (sampleRate <= 0.0) return false
    val hex = sessionId.replace("-", "").takeLast(8)
    val value = hex.toLongOrNull(16) ?: return false
    return value.toDouble() / 4_294_967_296.0 < sampleRate
  }
}
