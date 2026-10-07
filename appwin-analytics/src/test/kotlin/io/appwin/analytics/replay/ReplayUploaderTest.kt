@file:OptIn(AppwinInternalApi::class, ExperimentalCoroutinesApi::class)

package io.appwin.analytics.replay

import io.appwin.core.AppwinInternalApi
import io.appwin.core.analytics.AnalyticsConsent
import io.appwin.core.analytics.SendOutcome
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReplayUploaderTest {
  @get:Rule
  val tmp = TemporaryFolder()

  private val sent = mutableListOf<String>()
  private val outcomes = ArrayDeque<SendOutcome>()
  private var consent = AnalyticsConsent.GRANTED
  private var forbidden = 0
  private var reauthorizations = 0

  private fun queueWith(count: Int): ReplayQueue {
    val queue = ReplayQueue(File(tmp.root, "queue"))
    repeat(count) { seq ->
      val video = File(tmp.root, "v$seq.mp4").apply { writeBytes(ByteArray(8)) }
      queue.enqueue(video, buildJsonObject { put("seq", seq) })
      Thread.sleep(2)
    }
    return queue
  }

  private fun TestScope.uploader(queue: ReplayQueue) = ReplayUploader(
    queue = queue,
    sender = { meta, _ ->
      sent.add(meta)
      outcomes.removeFirstOrNull() ?: SendOutcome.OK
    },
    consent = { consent },
    onForbidden = { forbidden++ },
    scope = backgroundScope,
    retryDelayMs = { 1_000 },
    reauthorize = { reauthorizations++; true },
    now = { 0 },
  )

  @Test
  fun `200 deletes each segment, sent oldest first with sentAt`() = runTest {
    val queue = queueWith(2)
    uploader(queue).flushNow()
    assertEquals(2, sent.size)
    val first = Json.parseToJsonElement(sent[0]).jsonObject
    assertEquals("0", first["seq"].toString())
    assertEquals("\"1970-01-01T00:00:00.000Z\"", first["sentAt"].toString())
    assertTrue(queue.pending().isEmpty())
  }

  @Test
  fun `400 and 413 drop the segment and carry on`() = runTest {
    val queue = queueWith(2)
    outcomes.add(SendOutcome.FATAL)
    uploader(queue).flushNow()
    assertEquals(2, sent.size)
    assertTrue(queue.pending().isEmpty())
  }

  @Test
  fun `403 purges the queue and stops`() = runTest {
    val queue = queueWith(3)
    outcomes.add(SendOutcome.FORBIDDEN)
    val uploader = uploader(queue)
    uploader.flushNow()
    assertEquals(1, sent.size)
    assertEquals(1, forbidden)
    assertTrue(queue.pending().isEmpty())
    uploader.flushNow()
    assertEquals(1, sent.size)
  }

  @Test
  fun `a retryable failure keeps the segment and retries after the backoff`() = runTest {
    val queue = queueWith(1)
    outcomes.add(SendOutcome.RETRYABLE)
    uploader(queue).flushNow()
    assertEquals(1, sent.size)
    assertEquals(1, queue.pending().size)
    advanceTimeBy(1_001)
    runCurrent()
    assertEquals(2, sent.size)
    assertTrue(queue.pending().isEmpty())
  }

  @Test
  fun `a 401 re-bootstraps once per flush, then backs off`() = runTest {
    val queue = queueWith(1)
    outcomes.addAll(listOf(SendOutcome.UNAUTHORIZED, SendOutcome.OK))
    uploader(queue).flushNow()
    assertEquals(2, sent.size)
    assertEquals(1, reauthorizations)
    assertTrue(queue.pending().isEmpty())

    val stuck = queueWith(1)
    outcomes.addAll(listOf(SendOutcome.UNAUTHORIZED, SendOutcome.UNAUTHORIZED))
    uploader(stuck).flushNow()
    assertEquals(4, sent.size)
    assertEquals(2, reauthorizations)
    assertEquals(1, stuck.pending().size)
  }

  @Test
  fun `nothing leaves the device without GRANTED`() = runTest {
    val queue = queueWith(1)
    consent = AnalyticsConsent.UNKNOWN
    uploader(queue).flushNow()
    assertTrue(sent.isEmpty())
    assertEquals(1, queue.pending().size)
  }
}
