package io.appwin.analytics.replay

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReplayMetaTest {
  private val info = SegmentInfo(
    sessionId = "0192a6c4-1f2e-7abc-9def-0123456789ab",
    seq = 3,
    startedAtMs = 1_700_000_000_000,
    width = 192,
    height = 432,
    runtime = "android",
  )

  private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

  @Test
  fun `meta matches ReplaySegmentMetaSchema`() {
    val meta = ReplayMeta.build(
      info,
      endedAtMs = info.startedAtMs + 10_000,
      screens = listOf(ReplayScreen(0, "home")),
      touches = listOf(ReplayTouch(1_500, 0.25, 0.75)),
    )
    assertEquals(info.sessionId, meta.string("sessionId"))
    assertEquals(3, meta.getValue("seq").jsonPrimitive.int)
    assertEquals("video", meta.string("kind"))
    assertEquals("android", meta.string("runtime"))
    assertEquals("2023-11-14T22:13:20.000Z", meta.string("startedAt"))
    assertEquals("2023-11-14T22:13:30.000Z", meta.string("endedAt"))
    assertEquals(192, meta.getValue("width").jsonPrimitive.int)
    assertEquals(432, meta.getValue("height").jsonPrimitive.int)
    assertEquals("home", meta.getValue("screens").jsonArray[0].jsonObject.string("name"))
    val touch = meta.getValue("touches").jsonArray[0].jsonObject
    assertEquals("1500", touch.string("t"))
    assertEquals("0.25", touch.string("x"))
    assertFalse("sentAt" in meta)
  }

  @Test
  fun `lists are capped and values clamped to the schema bounds`() {
    val meta = ReplayMeta.build(
      info,
      endedAtMs = info.startedAtMs - 5,
      screens = List(150) { ReplayScreen(it.toLong(), "s".repeat(200)) },
      touches = List(1_200) { ReplayTouch(-4, 1.5, -0.2) },
    )
    assertEquals(meta.string("startedAt"), meta.string("endedAt"))
    assertEquals(100, meta.getValue("screens").jsonArray.size)
    assertEquals(128, meta.getValue("screens").jsonArray[0].jsonObject.string("name").length)
    val touches = meta.getValue("touches").jsonArray
    assertEquals(1_000, touches.size)
    assertEquals("0", touches[0].jsonObject.string("t"))
    assertEquals("1.0", touches[0].jsonObject.string("x"))
    assertEquals("0.0", touches[0].jsonObject.string("y"))
  }

  @Test
  fun `sentAt is stamped at send time`() {
    val meta = ReplayMeta.build(info, info.startedAtMs, emptyList(), emptyList())
    val sent = ReplayMeta.withSentAt(meta, 0)
    assertEquals("1970-01-01T00:00:00.000Z", sent.string("sentAt"))
    assertEquals(info.sessionId, sent.string("sessionId"))
  }

  @Test
  fun `segment info survives the disk round trip`() {
    assertEquals(info, SegmentInfo.fromJson(info.toJson()))
  }

  @Test
  fun `only known runtimes are accepted from a bridge`() {
    assertEquals("flutter", ReplayRuntime.normalize("flutter"))
    assertEquals("react_native", ReplayRuntime.normalize("react_native"))
    assertEquals(null, ReplayRuntime.normalize("web"))
  }
}
