@file:OptIn(AppwinInternalApi::class)

package io.appwin.core.analytics

import android.util.Log
import io.appwin.core.AppwinInternalApi
import io.appwin.core.consent.ConsentPurpose
import io.appwin.core.consent.ConsentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tuning knobs of the pipeline. One instance, shared names with the Swift
 * mirror - a change here must land there too.
 */
internal data class AnalyticsConfig(
  val flushAt: Int = 20,
  val flushIntervalMs: Long = 30_000,
  val maxBatch: Int = 500,
  val maxQueueEvents: Int = 10_000,
  val sessionTimeoutMs: Long = 30 * 60_000L,
  val maxSessionAgeMs: Long = 24 * 3_600_000L,
  val backoffBaseMs: Long = Backoff.INGEST_BASE_MS,
  val backoffCapMs: Long = Backoff.INGEST_CAP_MS,
  val quotaCooldownMs: Long = 60 * 60_000L,
)

/**
 * The analytics pipeline: one consumer coroutine serializes everything -
 * enqueue, flush, consent, session transitions - so none of the parts it
 * owns needs locks (the actor pattern, mirroring the Swift side).
 *
 * Contract (ADR-0036 §3): persisted queue, batched sends, multi-trigger
 * flush, exponential backoff with jitter, at-least-once with server-side
 * dedup by eventId, queue-before-consent. Public entry points never throw
 * and never block the caller: they post a command on a bounded channel.
 */
internal class EventPipeline(
  private val store: EventStore,
  private val sessions: SessionManager,
  private val consentStore: ConsentStore,
  private val sender: EventSender,
  private val config: AnalyticsConfig,
  private val prefs: AnalyticsPrefs,
  private val keyPrefix: String,
  /**
   * Re-bootstraps the SDK session after a 401. Returns false when the
   * bearer could not be refreshed.
   */
  private val reauthorize: suspend () -> Boolean,
  private val scope: CoroutineScope,
  private val now: () -> Long = System::currentTimeMillis,
) {
  internal sealed interface Command {
    data class Track(
      val name: String,
      val screen: String?,
      val props: Map<String, Any?>?,
      val occurredAtMs: Long,
      /** Shared with ad-signals adapters for cross-source dedup. */
      val eventId: String? = null,
    ) : Command

    /**
     * An event emitted by the SDK itself (`push_opened`...): no validation,
     * so the reserved names pass.
     */
    data class Automatic(
      val name: String,
      val props: Map<String, Any?>?,
      val occurredAtMs: Long,
      val eventId: String? = null,
    ) : Command

    /**
     * The consent store changed. Carries no value: the store is the single
     * source and is re-read when this runs, so commands handled late can't
     * apply a stale answer.
     */
    data object ConsentChanged : Command

    /** Rotate the current file and send every ready batch. */
    data object Flush : Command

    data object Foreground : Command

    data object Background : Command

    data object NetworkRegained : Command
  }

  private val channel = kotlinx.coroutines.channels.Channel<Command>(capacity = 64)
  private val backoff = Backoff(config.backoffBaseMs, config.backoffCapMs)
  private var attempt = 0
  private var quotaCooldownUntilMs: Long? = null
  private var retryJob: Job? = null
  private var timerJob: Job? = null
  private var consumer: Job? = null
  /** Whether the app is in the foreground, kept even while consent is denied. */
  private var inForeground = false

  /** `app_open` is out for this foreground period (the launch can be reported twice). */
  private var appOpenEmitted = false

  /**
   * Mirror of the session id for readers outside the consumer (the crash
   * handler runs on the dying thread and cannot wait for a command turn).
   */
  @Volatile
  var currentSessionId: String? = null
    private set

  /** Launches the consumer; all deferred I/O happens in its first turn. */
  fun start() {
    if (consumer != null) return
    consumer = scope.launch {
      store.start()
      // A queue left over from a run before the consent turned denied must
      // not survive it.
      if (consentStore.allDenied) store.purgeAll()
      for (command in channel) {
        runCatching { handle(command) }
          .onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Log.w(TAG, "analytics: command failed", it)
          }
      }
    }
  }

  /** Never blocks: a full channel drops the command (and says so). */
  fun submit(command: Command) {
    if (!channel.trySend(command).isSuccess) {
      Log.w(TAG, "analytics: command dropped, channel full")
    }
  }

  /** Test-only: without it, a consumer would leak from one test to the next. */
  internal fun stop() {
    retryJob?.cancel()
    timerJob?.cancel()
    consumer?.cancel()
    retryJob = null
    timerJob = null
    consumer = null
    channel.close()
  }

  private suspend fun handle(command: Command) {
    when (command) {
      is Command.Track -> track(command)
      is Command.Automatic -> automatic(command)
      is Command.ConsentChanged -> consentChanged()
      is Command.Flush -> flush()
      is Command.Foreground -> {
        inForeground = true
        foreground()
      }
      is Command.Background -> {
        inForeground = false
        appOpenEmitted = false
        // Denied on the three: nothing is enqueued, no session is touched.
        if (consentStore.allDenied) return
        appendAutomatic("app_background")
        sessions.onBackground()
        timerJob?.cancel()
        timerJob = null
        flush()
      }
      is Command.NetworkRegained -> {
        // The wait is over: retry immediately.
        attempt = 0
        retryJob?.cancel()
        retryJob = null
        flush()
      }
    }
  }

  /**
   * The foreground work. Run on a Foreground command, and again when consent
   * leaves "all denied" while the app is already in the foreground, so a
   * re-grant opens the session and emits `app_open` without waiting for the
   * next foreground.
   */
  private suspend fun foreground() {
    // Denied on the three: no session, no app_open, no install flag.
    if (consentStore.allDenied) return
    appendSessionEvents()
    if (!appOpenEmitted) {
      appOpenEmitted = true
      appendAutomatic("app_open")
    }
    startTimer()
    flush()
  }

  private suspend fun track(command: Command.Track) {
    if (consentStore.allDenied) return
    appendSessionEvents()
    recordDrops(
      store.append(
        WireEvent.line(
          name = command.name,
          occurredAtMs = command.occurredAtMs,
          sessionId = sessions.sessionId,
          screen = command.screen,
          props = command.props,
          eventId = command.eventId,
        ),
      ),
    )
    flushIfFull()
  }

  /** An SDK-emitted event: same path as [track], the name is not validated. */
  private suspend fun automatic(command: Command.Automatic) {
    if (consentStore.allDenied) return
    appendSessionEvents()
    appendAutomatic(command.name, command.props, command.occurredAtMs, command.eventId)
    flushIfFull()
  }

  private fun appendAutomatic(
    name: String,
    props: Map<String, Any?>? = null,
    occurredAtMs: Long = now(),
    eventId: String? = null,
  ) {
    if (consentStore.allDenied) return
    recordDrops(
      store.append(
        WireEvent.line(
          name = name,
          occurredAtMs = occurredAtMs,
          sessionId = sessions.sessionId,
          screen = null,
          props = props,
          eventId = eventId,
        ),
      ),
    )
  }

  private suspend fun flushIfFull() {
    if (canSend(consentStore.snapshot()) && store.queuedEventCount >= config.flushAt) {
      flush()
    }
  }

  private suspend fun consentChanged() {
    if (consentStore.allDenied) {
      store.purgeAll()
      sessions.reset()
      currentSessionId = null
      retryJob?.cancel()
      retryJob = null
      attempt = 0
    } else if (inForeground) {
      foreground()
    } else {
      flush()
    }
  }

  private suspend fun flush() {
    if (!canSend(consentStore.snapshot())) return
    val cooldownUntil = quotaCooldownUntilMs
    if (cooldownUntil != null && now() < cooldownUntil) return
    store.rotateCurrent()
    var reauthorized = false
    while (true) {
      val batch = store.nextReadyBatch() ?: return
      // One read decides and stamps: now, not when queued, the answer may
      // have come in between.
      val answers = consentStore.snapshot()
      if (!canSend(answers)) return
      val purposes = ConsentPurpose.entries.filter { answers[it] == ConsentState.GRANTED }
      when (sender.send(batch.lines.map { WireEvent.stamped(it, purposes) })) {
        SendOutcome.OK -> {
          store.delete(batch.file)
          attempt = 0
        }
        SendOutcome.QUOTA_EXCEEDED -> {
          // The server dropped the data by design; resending would be
          // noise. The queue keeps accepting (the plan can change),
          // overflow prunes.
          store.delete(batch.file)
          quotaCooldownUntilMs = now() + config.quotaCooldownMs
          return
        }
        SendOutcome.FATAL, SendOutcome.FORBIDDEN -> {
          recordDrops(batch.lines.size)
          store.delete(batch.file)
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

  /** Nothing leaves while a purpose is unanswered, or when none is granted. */
  private fun canSend(answers: Map<ConsentPurpose, ConsentState>): Boolean =
    answers.values.none { it == ConsentState.UNKNOWN } && answers.values.any { it == ConsentState.GRANTED }

  private fun scheduleRetry() {
    val delayMs = backoff.delayMs(attempt)
    attempt += 1
    retryJob?.cancel()
    retryJob = scope.launch {
      delay(delayMs)
      submit(Command.Flush)
    }
  }

  private fun startTimer() {
    timerJob?.cancel()
    timerJob = scope.launch {
      while (isActive) {
        delay(config.flushIntervalMs)
        // The store is consumer-confined: never read it from here. An empty
        // flush is a cheap no-op, so the tick submits unconditionally.
        submit(Command.Flush)
      }
    }
  }

  private fun appendSessionEvents() {
    val events = sessions.touch()
    currentSessionId = sessions.sessionId
    for (event in events) {
      val props = event.durationMs?.let { mapOf("duration_ms" to it) }
      recordDrops(
        store.append(
          WireEvent.line(
            name = event.name,
            occurredAtMs = event.occurredAtMs,
            sessionId = event.sessionId,
            screen = null,
            props = props,
          ),
        ),
      )
    }
  }

  /**
   * Overflow drops are invisible by nature; the persisted counter is the
   * trace, and the future diagnostics surface will read it.
   */
  private fun recordDrops(count: Int) {
    if (count <= 0) return
    val key = keyPrefix + "droppedCount"
    val total = (prefs.getString(key)?.toIntOrNull() ?: 0) + count
    prefs.putString(key, total.toString())
  }

  private companion object {
    const val TAG = "Appwin"
  }
}
