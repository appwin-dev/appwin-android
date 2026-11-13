package io.appwin.analytics.replay

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Mp4SegmentEncoderTest {
  private val dir: File = Files.createTempDirectory("encode").toFile()

  @Test
  fun `a segment without frames fails alone`() {
    val out = File(dir, "out.mp4")
    assertEquals(Mp4SegmentEncoder.Result.FAILED, Mp4SegmentEncoder.encode(emptyList(), 64, 64, out))
  }

  @Test
  fun `a device without an H264 encoder is unavailable`() {
    val frame = 0L to File(dir, "0.jpg").apply { writeBytes(ByteArray(4)) }
    val out = File(dir, "out.mp4")
    assertEquals(Mp4SegmentEncoder.Result.UNAVAILABLE, Mp4SegmentEncoder.encode(listOf(frame), 64, 64, out))
    assertFalse(out.exists())
  }
}
