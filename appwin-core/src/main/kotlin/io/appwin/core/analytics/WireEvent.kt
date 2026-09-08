package io.appwin.core.analytics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Serializes one event to its wire form (the exact JSON object the server
 * ingests) at `track` time. The disk queue stores wire lines, so what is
 * stored IS what is sent: no re-mapping at flush time, no schema drift
 * between the two.
 */
internal object WireEvent {

  fun line(
    name: String,
    occurredAtMs: Long,
    sessionId: String?,
    screen: String?,
    props: Map<String, Any?>?,
  ): String {
    val obj = buildJsonObject {
      put("eventId", UUID.randomUUID().toString().lowercase())
      put("name", name)
      put("occurredAt", IsoDate.format(occurredAtMs))
      if (sessionId != null) put("sessionId", sessionId)
      if (screen != null) put("screen", screen)
      if (!props.isNullOrEmpty()) {
        putJsonObject("props") {
          for ((key, value) in props) {
            when (value) {
              is String -> put(key, value)
              is Boolean -> put(key, value)
              is Int -> put(key, value)
              is Long -> put(key, value)
              is Float -> put(key, value)
              is Double -> put(key, value)
              // Filtered upstream by the public API; belt and braces here.
              else -> {}
            }
          }
        }
      }
    }
    return obj.toString()
  }
}

/**
 * ISO 8601 UTC with milliseconds. SimpleDateFormat rather than java.time:
 * minSdk 24 has no java.time without desugaring, and the SDK adds no
 * dependency for one date format. Thread-local: the formatter is not safe.
 */
internal object IsoDate {
  private val format = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
  }

  fun format(ms: Long): String = format.get()!!.format(Date(ms))
}
