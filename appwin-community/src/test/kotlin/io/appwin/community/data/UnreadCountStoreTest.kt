package io.appwin.community.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadCountStoreTest {
  private var now = 1_000_000L
  private var fetches = 0
  private var serverValue = 3

  private fun TestScope.store() = UnreadCountStore(
    scope = backgroundScope,
    fetch = { fetches++; serverValue },
    clock = { now },
  )

  @Test
  fun `refreshes are skipped while nobody collects`() = runTest(UnconfinedTestDispatcher()) {
    val store = store()
    store.refresh()
    assertEquals(0, fetches)
  }

  @Test
  fun `the first collector triggers a refresh and gets the value`() = runTest(UnconfinedTestDispatcher()) {
    val store = store()
    val values = mutableListOf<Int>()
    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { store.updates.collect { values += it } }
    assertEquals(1, fetches)
    assertEquals(listOf(3), values)
  }

  @Test
  fun `refreshes are throttled to one per interval`() = runTest(UnconfinedTestDispatcher()) {
    val store = store()
    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { store.updates.collect {} }
    store.refresh()
    now += 29_000
    store.refresh()
    assertEquals(1, fetches)
    now += 2_000
    store.refresh()
    assertEquals(2, fetches)
  }

  @Test
  fun `a bootstrap value counts as a fresh read and duplicates are suppressed`() =
    runTest(UnconfinedTestDispatcher()) {
      val store = store()
      store.update(5)
      val values = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
        store.updates.take(2).toList()
      }
      // Known value emitted on subscribe, and the refresh it triggers is throttled.
      assertEquals(0, fetches)
      store.update(5)
      store.update(7)
      assertEquals(listOf(5, 7), values.await())
    }
}
