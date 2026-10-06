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
