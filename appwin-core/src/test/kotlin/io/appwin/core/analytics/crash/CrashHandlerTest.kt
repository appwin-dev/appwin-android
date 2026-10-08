package io.appwin.core.analytics.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CrashHandlerTest {
  private class Recording : Thread.UncaughtExceptionHandler {
    val received = mutableListOf<Throwable>()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
      received.add(throwable)
    }
  }

  @Test
  fun `records the crash then chains the previous handler`() {
    val previous = Recording()
    val recorded = mutableListOf<Throwable>()
    val handler = CrashHandler(previous) { _, throwable -> recorded.add(throwable) }
    val error = RuntimeException("boom")

    handler.uncaughtException(Thread.currentThread(), error)

    assertSame(error, recorded.single())
    assertSame(error, previous.received.single())
  }

  @Test
  fun `still chains when recording throws`() {
    val previous = Recording()
    val handler = CrashHandler(previous) { _, _ -> error("disk full") }

    handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

    assertEquals(1, previous.received.size)
  }

  @Test
  fun `records only the first crash of the process`() {
    val previous = Recording()
    var records = 0
    val handler = CrashHandler(previous) { _, _ -> records += 1 }

    handler.uncaughtException(Thread.currentThread(), RuntimeException("first"))
    handler.uncaughtException(Thread.currentThread(), RuntimeException("second"))

    assertEquals(1, records)
    assertEquals(2, previous.received.size)
  }
}
