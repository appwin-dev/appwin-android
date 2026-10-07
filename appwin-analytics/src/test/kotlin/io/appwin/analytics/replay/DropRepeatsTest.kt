package io.appwin.analytics.replay

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class DropRepeatsTest {
  private val dir: File = Files.createTempDirectory("frames").toFile()

  private fun frame(offset: Long, content: String) =
    offset to File(dir, "$offset.jpg").apply { writeText(content) }

  @Test
  fun `a still run keeps its first frame and the segment's last two`() {
    val frames = listOf(
      frame(0, "a"), frame(1000, "a"), frame(2000, "a"),
      frame(3000, "b"), frame(4000, "b"), frame(5000, "b"),
    )
    assertEquals(listOf(0L, 3000L, 4000L, 5000L), Mp4SegmentEncoder.dropRepeats(frames).map { it.first })
  }

  @Test
  fun `changing frames are all kept`() {
    val frames = listOf(frame(0, "a"), frame(1000, "b"), frame(2000, "a"))
    assertEquals(listOf(0L, 1000L, 2000L), Mp4SegmentEncoder.dropRepeats(frames).map { it.first })
  }
}
