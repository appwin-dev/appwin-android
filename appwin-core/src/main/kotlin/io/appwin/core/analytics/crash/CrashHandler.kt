package io.appwin.core.analytics.crash

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Uncaught exception handler that records the crash, then hands the
 * throwable to whatever handler was installed before (the platform's, which
 * shows the dialog and kills the process, or Crashlytics / Sentry, which
 * keep working).
 *
 * Never throws: an exception escaping a default handler loses the original
 * crash and the chained handlers with it.
 */
internal class CrashHandler(
  private val previous: Thread.UncaughtExceptionHandler?,
  private val record: (Thread, Throwable) -> Unit,
) : Thread.UncaughtExceptionHandler {
  private val handling = AtomicBoolean(false)

  override fun uncaughtException(thread: Thread, throwable: Throwable) {
    // A second crash while recording the first (or on another thread at the
    // same time) goes straight to the chain: one report per process death.
    if (handling.compareAndSet(false, true)) {
      try {
        record(thread, throwable)
      } catch (_: Throwable) {
      }
    }
    if (previous != null) {
      previous.uncaughtException(thread, throwable)
    } else {
      // No platform handler means nobody will kill the process: do what
      // the platform's would, or the app hangs in a broken state.
      android.os.Process.killProcess(android.os.Process.myPid())
      exitProcess(10)
    }
  }

  internal companion object {
    @Volatile
    private var installed: CrashHandler? = null

    /**
     * Installs the handler once per process; later calls are no-ops, never
     * a second link in the chain. [record] therefore resolves its target
     * itself, at crash time.
     */
    @Synchronized
    fun install(record: (Thread, Throwable) -> Unit) {
      if (installed != null) return
      val handler = CrashHandler(Thread.getDefaultUncaughtExceptionHandler(), record)
      Thread.setDefaultUncaughtExceptionHandler(handler)
      installed = handler
    }
  }
}
