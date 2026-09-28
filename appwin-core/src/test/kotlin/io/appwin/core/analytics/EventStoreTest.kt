package io.appwin.core.analytics

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventStoreTest {
  private lateinit var directory: File

  @Before
  fun setUp() {
    directory = File.createTempFile("appwin-store-tests", "").apply {
      delete()
      mkdirs()
    }
  }

  @After
  fun tearDown() {
    directory.deleteRecursively()
  }

  private fun makeStore(maxBatch: Int = 500, maxQueueEvents: Int = 10_000): EventStore =
    EventStore(directory, maxBatch, maxQueueEvents).apply { start() }

  private fun eventLine(i: Int): String = """{"eventId":"e$i"}"""

  @Test
  fun `append fait la rotation au seuil maxBatch`() {
    val store = makeStore(maxBatch = 3)
    store.append(eventLine(1))
    store.append(eventLine(2))
    assertNull(store.nextReadyBatch())
    store.append(eventLine(3))
    val batch = store.nextReadyBatch()
    assertEquals(listOf(eventLine(1), eventLine(2), eventLine(3)), batch?.lines)
    assertEquals(3, store.queuedEventCount)
  }

  @Test
  fun `le debordement droppe le plus ancien fichier ready et compte les pertes`() {
    val store = makeStore(maxBatch = 2, maxQueueEvents = 4)
    var dropped = 0
    for (i in 1..5) dropped += store.append(eventLine(i))
    assertEquals(2, dropped)
    assertEquals(3, store.queuedEventCount)
    assertEquals(listOf(eventLine(3), eventLine(4)), store.nextReadyBatch()?.lines)
  }

  @Test
  fun `une ligne corrompue est droppee seule`() {
    val store = makeStore()
    val content = "${eventLine(1)}\n{\"eventId\":\"tru\n${eventLine(3)}\n"
    File(directory, "ready-${Uuid7.generate()}.jsonl").writeText(content)
    store.start()
    assertEquals(listOf(eventLine(1), eventLine(3)), store.nextReadyBatch()?.lines)
  }

  @Test
  fun `un fichier illisible est supprime`() {
    val store = makeStore()
    // A directory with a batch name: any read attempt fails.
    File(directory, "ready-${Uuid7.generate()}.jsonl").mkdirs()
    store.start()
    assertNull(store.nextReadyBatch())
    assertTrue(directory.list()!!.isEmpty())
  }

  @Test
  fun `les batchs ready reviennent en ordre chronologique`() {
    val store = makeStore()
    store.append(eventLine(1))
    store.rotateCurrent()
    store.append(eventLine(2))
    store.rotateCurrent()
    val first = store.nextReadyBatch()
    assertEquals(listOf(eventLine(1)), first?.lines)
    store.delete(first!!.file)
    assertEquals(listOf(eventLine(2)), store.nextReadyBatch()?.lines)
  }

  @Test
  fun `start reconstruit les compteurs apres un relaunch`() {
    val store = makeStore(maxBatch = 2)
    for (i in 1..3) store.append(eventLine(i))
    val relaunched = EventStore(directory, maxBatch = 2, maxQueueEvents = 10_000).apply { start() }
    assertEquals(3, relaunched.queuedEventCount)
    assertEquals(listOf(eventLine(1), eventLine(2)), relaunched.nextReadyBatch()?.lines)
  }

  @Test
  fun `purgeAll vide la file`() {
    val store = makeStore(maxBatch = 2)
    for (i in 1..3) store.append(eventLine(i))
    store.purgeAll()
    assertEquals(0, store.queuedEventCount)
    assertNull(store.nextReadyBatch())
  }
}
