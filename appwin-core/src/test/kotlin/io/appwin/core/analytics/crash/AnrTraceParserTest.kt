package io.appwin.core.analytics.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnrTraceParserTest {
  private val trace = """
    ----- pid 1234 at 2026-10-04 10:00:00.000 -----
    Cmd line: com.acme.app

    "Signal Catcher" daemon prio=10 tid=2 Runnable
      at dalvik.system.Other.run(Other.java:1)

    "main" prio=5 tid=1 Blocked
      | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72a3f2a0 self=0xb400
      at com.acme.app.Repo.load(Repo.kt:31)
      - waiting to lock <0x0b1e7f3a> (a java.lang.Object) held by thread 23
      at com.acme.app.MainActivity${'$'}onCreate${'$'}1.invoke(MainActivity.kt:58)
      at android.os.MessageQueue.nativePollOnce(Native method)
      native: #00 pc 000000000004e0cc  /apex/com.android.runtime/lib64/bionic/libc.so (syscall+28)
      at android.os.Looper.loop(Unknown Source:12)

    "OkHttp Dispatcher" prio=5 tid=24 Waiting
      at okhttp3.Dispatcher.run(Dispatcher.kt:1)
  """.trimIndent()

  @Test
  fun `extracts the main thread java frames only`() {
    val frames = AnrTraceParser.mainThreadFrames(
      trace.lineSequence(),
      InAppClassifier(listOf("com.acme.app")),
    )

    assertEquals(
      listOf(
        "com.acme.app.Repo.load",
        "com.acme.app.MainActivity\$onCreate\$1.invoke",
        "android.os.MessageQueue.nativePollOnce",
        "android.os.Looper.loop",
      ),
      frames.map { it.fn },
    )
    assertEquals(CrashReport.Frame("com.acme.app.Repo.load", "Repo.kt", 31, "com.acme.app", true), frames[0])
    assertEquals(null, frames[2].file)
    assertEquals(null, frames[2].line)
    assertEquals(null, frames[3].file)
    assertEquals(12, frames[3].line)
  }

  @Test
  fun `yields nothing for an unknown layout`() {
    val frames = AnrTraceParser.mainThreadFrames("garbage\nmore".lineSequence(), InAppClassifier(emptyList()))
    assertTrue(frames.isEmpty())
  }
}
