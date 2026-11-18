package io.appwin.analytics.replay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class ReplayTouch(val t: Long, val x: Double, val y: Double)

internal data class ReplayScreen(val t: Long, val name: String)

/**
 * Identity of a segment, written when it opens so a segment cut short by a
 * crash can still be encoded and described on the next launch.
 */
internal data class SegmentInfo(
  val sessionId: String,
  val seq: Int,
  val startedAtMs: Long,
  val width: Int,
  val height: Int,
  val runtime: String,
) {
  fun toJson(): JsonObject = buildJsonObject {
    put("sessionId", sessionId)
    put("seq", seq)
    put("startedAtMs", startedAtMs)
    put("width", width)
    put("height", height)
    put("runtime", runtime)
  }

  companion object {
    fun fromJson(json: JsonObject): SegmentInfo? = runCatching {
      SegmentInfo(
        sessionId = json.getValue("sessionId").jsonPrimitive.content,
        seq = json.getValue("seq").jsonPrimitive.int,
        startedAtMs = json.getValue("startedAtMs").jsonPrimitive.long,
        width = json.getValue("width").jsonPrimitive.int,
        height = json.getValue("height").jsonPrimitive.int,
        runtime = json["runtime"]?.jsonPrimitive?.content ?: ReplayRuntime.ANDROID,
      )
    }.getOrNull()
  }
}

internal object ReplayRuntime {
  const val ANDROID = "android"
  private val BRIDGED = setOf("flutter", "react_native")

  fun normalize(runtime: String): String? = runtime.takeIf { it == ANDROID || it in BRIDGED }
}

/** The `meta` field of `POST /api/sdk/v1/replays/segments` (`ReplaySegmentMetaSchema`). */
internal object ReplayMeta {
  fun build(
    info: SegmentInfo,
    endedAtMs: Long,
    screens: List<ReplayScreen>,
    touches: List<ReplayTouch>,
  ): JsonObject = buildJsonObject {
    put("sessionId", info.sessionId)
    put("seq", info.seq)
    put("kind", "video")
    put("runtime", info.runtime)
    put("startedAt", IsoTime.format(info.startedAtMs))
    put("endedAt", IsoTime.format(maxOf(endedAtMs, info.startedAtMs)))
    put("width", info.width)
    put("height", info.height)
    putJsonArray("screens") {
      for (screen in screens.take(ReplayLimits.MAX_SCREENS)) {
        addJsonObject {
          put("t", screen.t.coerceIn(0, MAX_OFFSET_MS))
          put("name", screen.name.take(MAX_SCREEN_NAME))
        }
      }
    }
    putJsonArray("touches") {
      for (touch in touches.take(ReplayLimits.MAX_TOUCHES)) {
        addJsonObject {
          put("t", touch.t.coerceIn(0, MAX_OFFSET_MS))
          put("x", touch.x.coerceIn(0.0, 1.0))
          put("y", touch.y.coerceIn(0.0, 1.0))
        }
      }
    }
  }

  /** Stamped at send time, so the server can correct a skewed device clock. */
  fun withSentAt(meta: JsonObject, nowMs: Long): JsonObject =
    JsonObject(meta + ("sentAt" to JsonPrimitive(IsoTime.format(nowMs))))

  private const val MAX_OFFSET_MS = 600_000L
  private const val MAX_SCREEN_NAME = 128
}

internal object IsoTime {
  private val format = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
  }

  fun format(ms: Long): String = format.get()!!.format(Date(ms))
}
