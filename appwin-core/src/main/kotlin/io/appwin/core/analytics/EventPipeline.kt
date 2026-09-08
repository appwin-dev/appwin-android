package io.appwin.core.analytics

import android.util.Log
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
  val backoffBaseMs: Long = 2_000,
  val backoffCapMs: Long = 300_000,
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
    ) : Command

    data class SetConsent(val consent: AnalyticsConsent) : Command

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

  /** Launches the consumer; all deferred I/O happens in its first turn. */
  fun start() {
    if (consumer != null) return
    consumer = scope.launch {
      store.start()
      // A queue left over from a run before the consent turned denied must
      // not survive it.
      if (consentStore.consent == AnalyticsConsent.DENIED) store.purgeAll()
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
      is Command.SetConsent -> setConsent(command.consent)
      is Command.Flush -> flush()
      is Command.Foreground -> {
        appendSessionEvents()
        startTimer()
        flush()
      }
      is Command.Background -> {
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

  private suspend fun track(command: Command.Track) {
    if (consentStore.consent == AnalyticsConsent.DENIED) return
    appendSessionEvents()
    recordDrops(
      store.append(
        WireEvent.line(
          name = command.name,
          occurredAtMs = command.occurredAtMs,
          sessionId = sessions.sessionId,
          screen = command.screen,
          props = command.props,
        ),
      ),
    )
    if (consentStore.consent == AnalyticsConsent.GRANTED && store.queuedEventCount >= config.flushAt) {
      flush()
    }
  }

  private suspend fun setConsent(consent: AnalyticsConsent) {
    if (consent == consentStore.consent) return
    consentStore.set(consent)
    when (consent) {
      AnalyticsConsent.DENIED -> {
        store.purgeAll()
        sessions.reset()
        retryJob?.cancel()
        retryJob = null
        attempt = 0
      }
      AnalyticsConsent.GRANTED -> flush()
      AnalyticsConsent.UNKNOWN -> {}
    }
  }

  private suspend fun flush() {
    if (consentStore.consent != AnalyticsConsent.GRANTED) return
    val cooldownUntil = quotaCooldownUntilMs
    if (cooldownUntil != null && now() < cooldownUntil) return
    store.rotateCurrent()
    var reauthorized = false
    while (true) {
      val batch = store.nextReadyBatch() ?: return
      if (consentStore.consent != AnalyticsConsent.GRANTED) return
      when (sender.send(batch.lines)) {
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
        SendOutcome.FATAL -> {
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
    for (event in sessions.touch()) {
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
