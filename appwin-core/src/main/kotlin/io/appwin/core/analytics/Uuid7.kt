package io.appwin.core.analytics

import kotlin.random.Random

/**
 * UUIDv7 generator (RFC 9562): 48-bit Unix-ms timestamp, then random bits.
 *
 * Analytics sessions use v7 so the id embeds its start time (the
 * raw_sessions_v3 pattern, cf. ADR-0041). No monotonic counter: the server
 * never orders events by session id, only groups by it.
 */
internal object Uuid7 {

  fun generate(nowMs: Long = System.currentTimeMillis(), random: Random = Random.Default): String {
    val bytes = ByteArray(16)
    for (i in 0 until 6) {
      bytes[i] = (nowMs ushr (8 * (5 - i))).toByte()
    }
    random.nextBytes(bytes, 6, 16)
    bytes[6] = (0x70 or (bytes[6].toInt() and 0x0F)).toByte()
    bytes[8] = (0x80 or (bytes[8].toInt() and 0x3F)).toByte()

    val hex = buildString(32) {
      for (b in bytes) {
        val v = b.toInt() and 0xFF
        append(HEX[v ushr 4])
        append(HEX[v and 0x0F])
      }
    }
    return buildString(36) {
      append(hex, 0, 8); append('-')
      append(hex, 8, 12); append('-')
      append(hex, 12, 16); append('-')
      append(hex, 16, 20); append('-')
      append(hex, 20, 32)
    }
  }

  /** Milliseconds embedded in the first 48 bits, for tests and file ordering. */
  fun timestampMs(uuid: String): Long? {
    val hex = uuid.replace("-", "")
    if (hex.length != 32) return null
    return hex.substring(0, 12).toLongOrNull(radix = 16)
  }

  private val HEX = "0123456789abcdef".toCharArray()
}
