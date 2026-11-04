package io.appwin.analytics.replay

import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReplayQueueTest {
  @get:Rule
  val tmp = TemporaryFolder()

  private var clock = 1_000L

  private fun queue(maxBytes: Long = 1_000_000) =
    ReplayQueue(File(tmp.root, "queue"), maxBytes = maxBytes, now = { clock })

  private fun video(name: String, bytes: Int): File =
    File(tmp.root, "$name.mp4").apply { writeBytes(ByteArray(bytes)) }

  private fun meta(seq: Int) = buildJsonObject { put("seq", seq) }

  @Test
  fun `entries come back oldest first with their meta`() {
    val queue = queue()
    queue.enqueue(video("a", 10), meta(0))
    clock += 1
    queue.enqueue(video("b", 10), meta(1))
    val pending = queue.pending()
    assertEquals(listOf(JsonPrimitive(0), JsonPrimitive(1)), pending.map { it.meta["seq"] })
    assertTrue(pending.all { it.video.exists() })
  }

  @Test
  fun `the byte cap drops the oldest segments`() {
    val queue = queue(maxBytes = 250)
    repeat(4) { seq ->
      clock += 1
      queue.enqueue(video("s$seq", 100), meta(seq))
    }
    val kept = queue.pending().map { it.meta["seq"] }
    assertEquals(listOf(JsonPrimitive(2), JsonPrimitive(3)), kept)
    assertTrue(queue.totalBytes() <= 250)
  }

  @Test
  fun `a video without its meta is swept once stale, never sent`() {
    val dir = File(tmp.root, "queue").apply { mkdirs() }
    val orphan = File(dir, "0000000000001-x.mp4").apply { writeBytes(ByteArray(4)) }
    orphan.setLastModified(0)
    clock = 120_000
    assertTrue(queue().pending().isEmpty())
    assertFalse(orphan.exists())
  }

  @Test
  fun `remove and purge delete the files`() {
    val queue = queue()
    queue.enqueue(video("a", 10), meta(0))
    queue.enqueue(video("b", 10), meta(1))
    val first = queue.pending().first()
    queue.remove(first)
    assertFalse(first.video.exists())
    assertEquals(1, queue.pending().size)
    queue.purge()
    assertTrue(queue.pending().isEmpty())
  }
}
