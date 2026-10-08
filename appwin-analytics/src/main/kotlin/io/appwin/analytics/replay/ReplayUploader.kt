@file:OptIn(AppwinInternalApi::class)

package io.appwin.analytics.replay

import io.appwin.core.AppwinInternalApi
import io.appwin.core.analytics.AnalyticsConsent
import io.appwin.core.analytics.AppwinAnalyticsHost
import io.appwin.core.analytics.SendOutcome
import io.appwin.core.network.MultipartFile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface SegmentSender {
  suspend fun send(meta: String, video: File): SendOutcome
}

/** Frozen URL (ADR-0052), served by the analytics service behind the SDK guard. */
internal object ApiSegmentSender : SegmentSender {
  override suspend fun send(meta: String, video: File): SendOutcome =
    AppwinAnalyticsHost.postMultipart(
      path = "/api/sdk/v1/replays/segments",
      fields = mapOf("meta" to meta),
      file = MultipartFile("segment", "segment.mp4", "video/mp4", video),
    )
}

/**
 * Drains [ReplayQueue] one segment at a time, oldest first, with one session
 * re-bootstrap per flush on a 401. A 403 means the studio switched replay
 * off: everything queued is purged and [onForbidden] stops the recorder until
 * a later launch finds it enabled again.
 */
internal class ReplayUploader(
  private val queue: ReplayQueue,
  private val sender: SegmentSender,
  private val consent: () -> AnalyticsConsent,
  private val onForbidden: () -> Unit,
  private val scope: CoroutineScope,
  private val retryDelayMs: (Int) -> Long,
  private val reauthorize: suspend () -> Boolean,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val sending = Mutex()

  /** Guarded by [sending]. */
  private var attempt = 0

  @Volatile
  private var retryJob: Job? = null

  @Volatile
  private var forbidden = false

  fun flush(resetBackoff: Boolean = false) {
    if (resetBackoff) retryJob?.cancel()
    scope.launch {
      if (resetBackoff) sending.withLock { attempt = 0 }
      flushNow()
    }
  }

  fun cancel() {
    retryJob?.cancel()
  }

  internal suspend fun flushNow() {
    sending.withLock {
      var reauthorized = false
      while (!forbidden && consent() == AnalyticsConsent.GRANTED) {
        val entry = queue.pending().firstOrNull() ?: return
        if (entry.video.length() > ReplayLimits.SEGMENT_MAX_BYTES) {
          queue.remove(entry)
          continue
        }
        val meta = ReplayMeta.withSentAt(entry.meta, now()).toString()
        when (sender.send(meta, entry.video)) {
          // FATAL too: a deterministic refusal (400, 413) would block every
          // segment behind it.
          SendOutcome.OK, SendOutcome.QUOTA_EXCEEDED, SendOutcome.FATAL -> {
            queue.remove(entry)
            attempt = 0
          }
          SendOutcome.FORBIDDEN -> {
            forbidden = true
            retryJob?.cancel()
            queue.purge()
            onForbidden()
            return
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

  private fun scheduleRetry() {
    val delayMs = retryDelayMs(attempt)
    attempt += 1
    retryJob?.cancel()
    retryJob = scope.launch {
      delay(delayMs)
      flushNow()
    }
  }
}
