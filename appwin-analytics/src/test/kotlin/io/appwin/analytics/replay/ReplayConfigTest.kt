package io.appwin.analytics.replay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayConfigTest {
  private fun parse(raw: String) = ReplayConfig.from(Json.parseToJsonElement(raw).jsonObject)

  @Test
  fun `a missing config masks everything and records every session`() {
    assertEquals(ReplayConfig(1.0, maskAllText = true, maskAllImages = true), ReplayConfig.from(null))
  }

  @Test
  fun `fields are read and the rate is clamped`() {
    assertEquals(
      ReplayConfig(0.25, maskAllText = false, maskAllImages = true),
      parse("""{"sampleRate":0.25,"maskAllText":false}"""),
    )
    assertEquals(1.0, parse("""{"sampleRate":4}""").sampleRate, 0.0)
    assertEquals(0.0, parse("""{"sampleRate":-1}""").sampleRate, 0.0)
  }

  @Test
  fun `a malformed field keeps its default rather than unmasking`() {
    val config = parse("""{"maskAllText":"no","maskAllImages":null,"sampleRate":"x"}""")
    assertTrue(config.maskAllText)
    assertTrue(config.maskAllImages)
    assertEquals(1.0, config.sampleRate, 0.0)
  }

  @Test
  fun `sampling reads the last 8 hex digits of the session id`() {
    val low = "0192a6c4-1f2e-7abc-9def-012300000000"
    val half = "0192a6c4-1f2e-7abc-9def-012380000000"
    val high = "0192a6c4-1f2e-7abc-9def-0123ffffffff"
    assertTrue(ReplaySampling.isSampled(low, 0.01))
    assertFalse(ReplaySampling.isSampled(half, 0.5))
    assertTrue(ReplaySampling.isSampled(half, 0.51))
    assertFalse(ReplaySampling.isSampled(high, 0.99))
    assertTrue(ReplaySampling.isSampled(high, 1.0))
    assertFalse(ReplaySampling.isSampled(low, 0.0))
  }

  @Test
  fun `sampling is the same for the same session`() {
    val id = "0192a6c4-1f2e-7abc-9def-0123456789ab"
    val first = ReplaySampling.isSampled(id, 0.3)
    repeat(5) { assertEquals(first, ReplaySampling.isSampled(id, 0.3)) }
    // 0x456789ab / 2^32 is about 0.2712.
    assertTrue(ReplaySampling.isSampled(id, 0.272))
    assertFalse(ReplaySampling.isSampled(id, 0.271))
  }

  @Test
  fun `frames are two pixels per dp, in multiples of 16`() {
    // 1080 x 2400 px at 2.75: 393 x 873 dp, twice that.
    assertEquals(784 to 1744, ScreenCapturer.frameSize(1080, 2400, 2.75f))
    // Below 2 px per dp the screen's own pixels are the cap.
    assertEquals(720 to 1280, ScreenCapturer.frameSize(720, 1280, 1.5f))
    assertEquals(64 to 64, ScreenCapturer.frameSize(10, 10, 3f))
  }
}
