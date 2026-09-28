package io.appwin.core.analytics

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Scripted sender: pops one outcome per call, then answers OK. */
private class FakeSender(script: List<SendOutcome> = emptyList()) : EventSender {
  val batches = mutableListOf<List<String>>()
  private val script = script.toMutableList()
  override suspend fun send(lines: List<String>): SendOutcome {
    batches.add(lines)
    return if (script.isEmpty()) SendOutcome.OK else script.removeAt(0)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EventPipelineTest {
  private lateinit var directory: File
  private lateinit var prefs: InMemoryAnalyticsPrefs
  private lateinit var store: EventStore
  private var nowMs = 1_788_500_000_000L
  private var reauthorizeCalls = 0
  private var pipelineScope: CoroutineScope? = null

  @Before
  fun setUp() {
    directory = File.createTempFile("appwin-pipeline-tests", "").apply {
      delete()
      mkdirs()
    }
    prefs = InMemoryAnalyticsPrefs()
    nowMs = 1_788_500_000_000L
    reauthorizeCalls = 0
  }

  @After
  fun tearDown() {
    pipelineScope?.cancel()
    directory.deleteRecursively()
  }

  private fun TestScope.makePipeline(
    sender: FakeSender,
    consent: AnalyticsConsent = AnalyticsConsent.GRANTED,
    maxBatch: Int = 500,
  ): EventPipeline {
    val config = AnalyticsConfig(maxBatch = maxBatch, backoffBaseMs = 20, backoffCapMs = 20)
    val keyPrefix = "appwin.analytics.test."
    // Explicit even for the granted default: the tests must not depend on it.
    val consentStore = ConsentStore(prefs, keyPrefix)
    consentStore.set(consent)
    store = EventStore(directory, maxBatch, config.maxQueueEvents)
    val pipeline = EventPipeline(
      store = store,
      sessions = SessionManager(
        prefs, keyPrefix, appVersion = "1.0.0",
        timeoutMs = config.sessionTimeoutMs, maxAgeMs = config.maxSessionAgeMs, now = { nowMs },
      ),
      consentStore = consentStore,
      sender = sender,
      config = config,
      prefs = prefs,
      keyPrefix = keyPrefix,
      reauthorize = { reauthorizeCalls += 1; true },
      // Not backgroundScope: advanceUntilIdle does not drive its jobs. A
      // scope on the test scheduler does, and tearDown cancels it.
      scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        .also { pipelineScope = it },
      now = { nowMs },
    )
    pipeline.start()
    return pipeline
  }

  private fun track(pipeline: EventPipeline, name: String = "spot_saved") {
    pipeline.submit(EventPipeline.Command.Track(name, screen = null, props = null, occurredAtMs = nowMs))
  }

  @Test
  fun `un flush reussi envoie install, session_start puis le custom et vide la file`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    pipeline.submit(
      EventPipeline.Command.Track("spot_saved", null, mapOf("plan" to "pro"), nowMs),
    )
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(1, sender.batches.size)
    val names = sender.batches[0].map {
      Json.parseToJsonElement(it).jsonObject["name"]!!.jsonPrimitive.content
    }
    assertEquals(listOf("app_install", "session_start", "spot_saved"), names)
    assertEquals(0, store.queuedEventCount)
  }

  @Test
  fun `un quota depasse supprime le batch et gele les envois pendant le cooldown`() = runTest {
    val sender = FakeSender(script = listOf(SendOutcome.QUOTA_EXCEEDED))
    val pipeline = makePipeline(sender)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, store.queuedEventCount)

    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals("le cooldown doit bloquer le second flush", 1, sender.batches.size)

    nowMs += 61 * 60_000
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(2, sender.batches.size)
  }

  @Test
  fun `un refus deterministe droppe le batch et continue avec les suivants`() = runTest {
    val sender = FakeSender(script = listOf(SendOutcome.FATAL))
    val pipeline = makePipeline(sender, maxBatch = 2)
    repeat(4) { track(pipeline, "event_$it") }
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, store.queuedEventCount)
    assertEquals("2", prefs.getString("appwin.analytics.test.droppedCount"))
  }

  @Test
  fun `un 401 rebootstrape une fois et renvoie le meme batch`() = runTest {
    val sender = FakeSender(script = listOf(SendOutcome.UNAUTHORIZED))
    val pipeline = makePipeline(sender)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, store.queuedEventCount)
    assertEquals(1, reauthorizeCalls)
    assertEquals(2, sender.batches.size)
    assertEquals(sender.batches[0], sender.batches[1])
  }

  @Test
  fun `une erreur transitoire garde le batch et reessaie apres le backoff`() = runTest {
    val sender = FakeSender(script = listOf(SendOutcome.RETRYABLE))
    val pipeline = makePipeline(sender)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(2, sender.batches.size)
    assertEquals(0, store.queuedEventCount)
    assertEquals(0, reauthorizeCalls)
  }

  @Test
  fun `consentement unknown - la file se remplit mais rien ne part`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = AnalyticsConsent.UNKNOWN)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, sender.batches.size)
    assertEquals("install + session_start + custom attendent", 3, store.queuedEventCount)
  }

  @Test
  fun `accorder le consentement envoie le backlog`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = AnalyticsConsent.UNKNOWN)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.SetConsent(AnalyticsConsent.GRANTED))
    advanceUntilIdle()
    assertEquals(1, sender.batches.size)
    assertEquals(0, store.queuedEventCount)
  }

  @Test
  fun `refuser le consentement purge tout et rend le pipeline muet`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = AnalyticsConsent.UNKNOWN)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.SetConsent(AnalyticsConsent.DENIED))
    advanceUntilIdle()
    assertEquals(0, store.queuedEventCount)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, sender.batches.size)
    assertEquals(0, store.queuedEventCount)
  }
}
