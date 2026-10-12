package io.appwin.core.analytics.crash

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrashStoreTest {
  private lateinit var directory: File

  @Before
  fun setUp() {
    directory = File.createTempFile("appwin-crash-tests", "").apply {
      delete()
      mkdirs()
    }
  }

  @After
  fun tearDown() {
    directory.deleteRecursively()
  }

  @Test
  fun `writes one file per crash without leaving a temp file`() {
    val store = CrashStore(File(directory, "crashes"))
    assertTrue(store.write("abc", """{"crashId":"abc"}"""))
    val names = File(directory, "crashes").list()!!.toSet()
    assertEquals(setOf("abc.json"), names)
    assertEquals("""{"crashId":"abc"}""", store.pending(10).single().json)
  }

  @Test
  fun `keeps the newest reports past the cap`() {
    val store = CrashStore(directory, maxReports = 3)
    for (i in 1..5) {
      store.write("c$i", """{"i":$i}""")
      // Distinct mtimes: ordering is by modification time.
      File(directory, "c$i.json").setLastModified(1_000_000L * i)
    }
    assertEquals(3, store.count())
    assertEquals(listOf("c3.json", "c4.json", "c5.json"), store.pending(10).map { it.file.name })
  }

  @Test
  fun `ignores and cleans stale temp files from a write cut short`() {
    val store = CrashStore(directory)
    File(directory, "dead.json.tmp").apply {
      writeText("{\"half")
      setLastModified(0)
    }
    File(directory, "live.json.tmp").writeText("{\"half")
    assertTrue(store.pending(10).isEmpty())
    assertEquals(setOf("live.json.tmp"), directory.list()!!.toSet())
  }

  @Test
  fun `purge removes every report`() {
    val store = CrashStore(directory)
    store.write("a", "{}")
    store.purgeAll()
    assertEquals(0, store.count())
  }
}
