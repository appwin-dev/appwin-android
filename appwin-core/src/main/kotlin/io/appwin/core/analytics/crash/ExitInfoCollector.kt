package io.appwin.core.analytics.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import io.appwin.core.analytics.AnalyticsPrefs
import java.io.InputStream
import java.util.UUID

/**
 * ANRs and native crashes from the previous runs, read on launch from
 * `ApplicationExitInfo` (API 30+). No NDK, no watchdog thread: the system
 * already records both, with the main thread's trace for an ANR.
 *
 * Java crashes are skipped: [CrashHandler] captured them with more context.
 */
internal class ExitInfoCollector(
  private val context: Context,
  private val prefs: AnalyticsPrefs,
  private val keyPrefix: String,
  private val classifier: InAppClassifier,
  private val now: () -> Long = System::currentTimeMillis,
) {
  /** Reports for the exits not seen yet. Blocking I/O: call off the main thread. */
  fun collect(crashContext: CrashContext): List<CrashReport> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
    return collectApi30(crashContext)
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private fun collectApi30(crashContext: CrashContext): List<CrashReport> {
    val key = keyPrefix + "crash.lastExitAt"
    val last = prefs.getString(key)?.toLongOrNull()
    if (last == null) {
      // First run with crash reporting: the history predates the adoption
      // and may span app versions we cannot attribute. Start from now.
      prefs.putString(key, now().toString())
      return emptyList()
    }
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      ?: return emptyList()
    val exits = runCatching {
      manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
    }.getOrDefault(emptyList())
    val fresh = exits.filter { it.timestamp > last }
    if (fresh.isEmpty()) return emptyList()
    prefs.putString(key, fresh.maxOf { it.timestamp }.toString())
    return fresh.mapNotNull { report(it, crashContext) }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private fun report(info: ApplicationExitInfo, crashContext: CrashContext): CrashReport? {
    val (kind, type, frames) = when (info.reason) {
      ApplicationExitInfo.REASON_ANR ->
        Triple(CrashReport.Kind.ANR, "ApplicationNotResponding", anrFrames(info))
      // The tombstone behind traceInputStream is a protobuf (API 31+): its
      // frames wait for symbolication (ADR-0056 phase 3).
      ApplicationExitInfo.REASON_CRASH_NATIVE ->
        Triple(CrashReport.Kind.CRASH, "NativeCrash", emptyList())
      else -> return null
    }
    return CrashReport(
      crashId = UUID.randomUUID().toString().lowercase(),
      kind = kind,
      occurredAtMs = info.timestamp,
      sessionId = null,
      screen = null,
      exceptionType = type,
      exceptionMessage = info.description,
      frames = frames,
      // Best effort: the version at exit time is not recorded by the system.
      context = crashContext,
      breadcrumbs = emptyList(),
    )
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private fun anrFrames(info: ApplicationExitInfo): List<CrashReport.Frame> =
    runCatching { info.traceInputStream?.let { AnrTraceParser.mainThreadFrames(it, classifier) } }
      .getOrNull()
      .orEmpty()

  private companion object {
    const val MAX_EXITS = 16
  }
}

/**
 * Extracts the main thread's Java frames from an ANR trace (the text dump
 * `debuggerd` writes: one block per thread, `"main" prio=5 tid=1 ...` then
 * `  at pkg.Class.method(File.kt:12)` lines, blank line between threads).
 * Lock and native lines are skipped. Best effort: an unknown layout yields
 * no frames, never an exception.
 */
internal object AnrTraceParser {
  private val frameLine = Regex("""^\s*at\s+([^\s(]+)\((.*)\)\s*$""")

  fun mainThreadFrames(stream: InputStream, classifier: InAppClassifier): List<CrashReport.Frame> =
    stream.bufferedReader().useLines { mainThreadFrames(it, classifier) }

  fun mainThreadFrames(lines: Sequence<String>, classifier: InAppClassifier): List<CrashReport.Frame> {
    val frames = ArrayList<CrashReport.Frame>()
    var inMain = false
    for (line in lines) {
      if (!inMain) {
        if (line.startsWith("\"main\"")) inMain = true
        continue
      }
      if (line.isBlank()) break
      val match = frameLine.matchEntire(line) ?: continue
      val qualified = match.groupValues[1]
      val className = qualified.substringBeforeLast('.', "")
      val methodName = qualified.substringAfterLast('.')
      val location = match.groupValues[2]
      val fileName = location.substringBefore(':').takeIf { it.contains('.') && !it.contains(' ') }
      val lineNumber = location.substringAfter(':', "").toIntOrNull()
      frames.add(CrashFrames.frame(className, methodName, fileName, lineNumber, classifier))
      if (frames.size >= CrashReport.MAX_FRAMES) break
    }
    return frames
  }
}
