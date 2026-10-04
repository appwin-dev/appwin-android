package io.appwin.core.analytics.crash

import android.util.Log
import io.appwin.core.analytics.AnalyticsConsent
import io.appwin.core.analytics.Backoff
import io.appwin.core.analytics.SendOutcome
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Crash capture and upload (ADR-0056). Lives next to the event pipeline and
 * shares its consent, but not its queue: a report is written synchronously
 * by the dying thread, where the pipeline's channel would never be drained.
 *
 * Consent: DENIED records nothing and purges the backlog; UNKNOWN records
 * but never sends; GRANTED sends on start, on [flush] and after a
 * non-fatal.
 */
internal class CrashReporter(
  private val store: CrashStore,
  private val sender: CrashSender,
  private val breadcrumbs: Breadcrumbs,
  private val classifier: InAppClassifier,
  private val crashContext: CrashContext,
  private val storedConsent: () -> AnalyticsConsent,
  private val sessionId: () -> String?,
  private val reauthorize: suspend () -> Boolean,
  private val scope: CoroutineScope,
  private val backoff: Backoff = Backoff(baseMs = 2_000, capMs = 300_000),
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val sending = Mutex()

  /**
   * The pipeline persists a consent change on its own consumer turn; this
   * makes the change effective here at once (no report written after a
   * DENIED, no GRANTED flush seeing the stale UNKNOWN).
   */
  @Volatile
  private var consentOverride: AnalyticsConsent? = null

  /** Guarded by [sending]. */
  private var attempt = 0

  @Volatile
  private var retryJob: Job? = null

  /**
   * When the last fatal React Native error was bridged in, or 0. React
   * Native rethrows that same error natively right after, as a
   * `JavascriptException` the uncaught handler would report a second time.
   */
  @Volatile
  private var bridgedRnFatalAtMs = 0L

  /** Runs on the crashing thread: synchronous, no coroutine, no lock. */
  fun onUncaught(throwable: Throwable) {
    if (consent() == AnalyticsConsent.DENIED) return
    if (isBridgedRnFatalEcho(throwable)) {
      bridgedRnFatalAtMs = 0L
      return
    }
    val report = report(CrashReport.Kind.CRASH, throwable)
    store.write(report.crashId, report.toJson().toString())
  }

  fun recordError(throwable: Throwable) {
    if (consent() == AnalyticsConsent.DENIED) return
    // Built on the caller's thread so session and breadcrumbs are those of
    // the moment of the error, not of the write.
    val report = report(CrashReport.Kind.NON_FATAL, throwable)
    scope.launch {
      store.write(report.crashId, report.toJson().toString())
      flushNow()
    }
  }

  /**
   * A Dart or JS error relayed by a cross-platform plugin. Written before
   * returning, unlike [recordError]: React Native calls this right before
   * its runtime kills the process, so a deferred write would be lost.
   * Returns whether the report was stored.
   */
  fun recordBridged(
    runtime: String,
    fatal: Boolean,
    type: String,
    message: String?,
    frames: List<Map<String, Any?>>,
  ): Boolean {
    val bridged = CrashReport.Runtime.bridged(runtime) ?: return false
    if (consent() == AnalyticsConsent.DENIED) return false
    val report = report(
      kind = if (fatal) CrashReport.Kind.CRASH else CrashReport.Kind.NON_FATAL,
      type = type,
      message = message,
      frames = BridgedFrames.from(frames),
      runtime = bridged,
    )
    val stored = store.write(report.crashId, report.toJson().toString())
    if (stored && fatal && bridged == CrashReport.Runtime.REACT_NATIVE) bridgedRnFatalAtMs = now()
    if (stored) scope.launch { flushNow() }
    return stored
  }

  /** Previous runs' ANRs and native crashes, then the backlog. */
  fun start(exitInfo: ExitInfoCollector?) {
    scope.launch {
      if (consent() == AnalyticsConsent.DENIED) {
        store.purgeAll()
        return@launch
      }
      exitInfo?.let { collector ->
        runCatching { collector.collect(crashContext) }
          .onFailure { Log.w(TAG, "crashes: exit info unavailable", it) }
          .getOrNull()
          ?.forEach { store.write(it.crashId, it.toJson().toString()) }
      }
      flushNow()
    }
  }

  fun onEvent(name: String) = breadcrumbs.event(name)

  fun onScreen(name: String) = breadcrumbs.screen(name)

  fun onConsentChanged(newValue: AnalyticsConsent) {
    consentOverride = newValue
    when (newValue) {
      AnalyticsConsent.DENIED -> {
        retryJob?.cancel()
        breadcrumbs.clear()
        scope.launch { sending.withLock { store.purgeAll() } }
      }
      AnalyticsConsent.GRANTED -> flush()
      AnalyticsConsent.UNKNOWN -> {}
    }
  }

  /** Network regained or an explicit flush: the wait is over. */
  fun flush(resetBackoff: Boolean = false) {
    if (resetBackoff) retryJob?.cancel()
    scope.launch {
      if (resetBackoff) sending.withLock { attempt = 0 }
      flushNow()
    }
  }

  internal suspend fun flushNow() {
    sending.withLock {
      var reauthorized = false
      while (consent() == AnalyticsConsent.GRANTED) {
        val batch = store.pending(BATCH_SIZE)
        if (batch.isEmpty()) return
        when (sender.send(batch.map { it.json })) {
          // FATAL too: retrying a deterministic refusal would block every
          // report queued behind it.
          SendOutcome.OK, SendOutcome.QUOTA_EXCEEDED, SendOutcome.FATAL -> {
            batch.forEach { store.delete(it.file) }
            attempt = 0
          }
          SendOutcome.UNAUTHORIZED -> {
            if (!reauthorized && reauthorize()) {
              reauthorized = true
              continue
            }
            scheduleRetry()
            return
          }
          SendOutcome.RETRYABLE -> {
            scheduleRetry()
            return
          }
        }
      }
    }
  }

  private fun isBridgedRnFatalEcho(throwable: Throwable): Boolean {
    val at = bridgedRnFatalAtMs
    if (at == 0L || now() - at > RN_ECHO_WINDOW_MS) return false
    // By name: the SDK does not depend on React Native.
    return CrashFrames.causeChain(throwable).any { it.javaClass.name == RN_JS_EXCEPTION }
  }

  private fun consent(): AnalyticsConsent = consentOverride ?: storedConsent()

  private fun scheduleRetry() {
    val delayMs = backoff.delayMs(attempt)
    attempt += 1
    retryJob?.cancel()
    retryJob = scope.launch {
      delay(delayMs)
      flushNow()
    }
  }

  private fun report(kind: CrashReport.Kind, throwable: Throwable): CrashReport {
    val root = CrashFrames.rootCause(throwable)
    return report(
      kind = kind,
      type = root.javaClass.name,
      message = root.message ?: throwable.message,
      frames = CrashFrames.from(root, classifier),
    )
  }

  private fun report(
    kind: CrashReport.Kind,
    type: String,
    message: String?,
    frames: List<CrashReport.Frame>,
    runtime: CrashReport.Runtime = CrashReport.Runtime.ANDROID,
  ) = CrashReport(
    crashId = UUID.randomUUID().toString().lowercase(),
    kind = kind,
    occurredAtMs = now(),
    sessionId = sessionId(),
    screen = breadcrumbs.currentScreen,
    exceptionType = type,
    exceptionMessage = message?.take(CrashReport.MAX_MESSAGE),
    frames = frames,
    context = crashContext,
    breadcrumbs = breadcrumbs.snapshot(),
    runtime = runtime,
  )

  internal companion object {
    private const val TAG = "Appwin"
    const val BATCH_SIZE = 20
    private const val RN_JS_EXCEPTION = "com.facebook.react.common.JavascriptException"
    private const val RN_ECHO_WINDOW_MS = 10_000L

    /**
     * What the process-wide [CrashHandler] records into. Swapped, never
     * re-installed: the handler is chained once per process.
     */
    @Volatile
    var active: CrashReporter? = null

    fun install(reporter: CrashReporter) {
      active = reporter
      CrashHandler.install { _, throwable -> active?.onUncaught(throwable) }
    }
  }
}
