package io.appwin.analytics.replay

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SegmentStoreTest {
  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun `a segment cut short is read back with its frames, taps and screens`() {
    val store = SegmentStore(File(tmp.root, "frames"))
    val info = SegmentInfo("0192a6c4-1f2e-7abc-9def-0123456789ab", 2, 5_000, 192, 432, "android")
    val dir = store.open(info)!!
    File(dir, "2000.jpg").writeBytes(ByteArray(3))
    File(dir, "0.jpg").writeBytes(ByteArray(3))
    File(dir, "1000.jpg.tmp").writeBytes(ByteArray(3))
    store.appendScreen(dir, ReplayScreen(0, "home"))
    store.appendTouch(dir, ReplayTouch(1_200, 0.5, 0.25))
    // What a process killed mid-write leaves behind.
    File(dir, "events.jsonl").appendText("{\"t\":13")

    val recorded = store.read(dir)!!
    assertEquals(info, recorded.info)
    assertEquals(listOf(0L, 2_000L), recorded.frames.map { it.first })
    assertEquals(listOf(ReplayScreen(0, "home")), recorded.screens)
    assertEquals(listOf(ReplayTouch(1_200, 0.5, 0.25)), recorded.touches)
    assertEquals(listOf(dir), store.leftovers())

    store.purge()
    assertTrue(store.leftovers().isEmpty())
  }
}
