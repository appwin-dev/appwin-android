@file:OptIn(AppwinInternalApi::class)

package io.appwin.core.analytics

import io.appwin.core.AppwinInternalApi
import io.appwin.core.consent.ConsentPurpose
import io.appwin.core.consent.ConsentState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
  private lateinit var consentStore: ConsentStore

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
    consent: ConsentState = ConsentState.GRANTED,
    maxBatch: Int = 500,
  ): EventPipeline {
    val config = AnalyticsConfig(maxBatch = maxBatch, backoffBaseMs = 20, backoffCapMs = 20)
    val keyPrefix = "appwin.analytics.test."
    // Explicit even for the granted default: the tests must not depend on it.
    consentStore = ConsentStore(prefs, keyPrefix)
    consentStore.set(consent, null)
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

  /** What `AppwinCore.setConsent` does: write the store now, then signal. */
  private fun setConsent(pipeline: EventPipeline, state: ConsentState, purpose: ConsentPurpose? = null) {
    consentStore.set(state, purpose)
    pipeline.submit(EventPipeline.Command.ConsentChanged)
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

  private fun names(sender: FakeSender): List<String> =
    sender.batches.flatten().map {
      Json.parseToJsonElement(it).jsonObject["name"]!!.jsonPrimitive.content
    }

  @Test
  fun `app_open une fois par premier plan, app_background a chaque arret`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    pipeline.submit(EventPipeline.Command.Foreground)
    pipeline.submit(EventPipeline.Command.Foreground)
    pipeline.submit(EventPipeline.Command.Background)
    runCurrent()  // not advanceUntilIdle: the foreground timer never ends
    pipeline.stop()  // else runTest drains the timer forever
    assertEquals(listOf("app_install", "session_start", "app_open", "app_background"), names(sender))
  }

  @Test
  fun `un nouveau premier plan apres l arret remet app_open`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    pipeline.submit(EventPipeline.Command.Foreground)
    pipeline.submit(EventPipeline.Command.Background)
    pipeline.submit(EventPipeline.Command.Foreground)
    runCurrent()  // not advanceUntilIdle: the foreground timer never ends
    pipeline.stop()  // else runTest drains the timer forever
    assertEquals(
      listOf("app_install", "session_start", "app_open", "app_background", "app_open"),
      names(sender),
    )
  }

  @Test
  fun `un event automatique passe malgre le nom reserve`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    pipeline.submit(EventPipeline.Command.Automatic("push_opened", mapOf("delivery_id" to "d1"), nowMs))
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    val last = sender.batches.single().last()
    assertTrue(last.contains("\"name\":\"push_opened\""))
    assertTrue(last.contains("\"delivery_id\":\"d1\""))
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
    val pipeline = makePipeline(sender, consent = ConsentState.UNKNOWN)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, sender.batches.size)
    assertEquals("install + session_start + custom attendent", 3, store.queuedEventCount)
  }

  @Test
  fun `accorder le consentement envoie le backlog`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = ConsentState.UNKNOWN)
    track(pipeline)
    setConsent(pipeline, ConsentState.GRANTED)
    advanceUntilIdle()
    assertEquals(1, sender.batches.size)
    assertEquals(0, store.queuedEventCount)
  }

  @Test
  fun `refuser le consentement purge tout et rend le pipeline muet`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = ConsentState.UNKNOWN)
    track(pipeline)
    setConsent(pipeline, ConsentState.DENIED)
    advanceUntilIdle()
    assertEquals(0, store.queuedEventCount)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, sender.batches.size)
    assertEquals(0, store.queuedEventCount)
  }

  private fun purposesOf(lines: List<String>): List<List<String>> = lines.map { line ->
    Json.parseToJsonElement(line).jsonObject["purposes"]!!.jsonArray.map { it.jsonPrimitive.content }
  }

  @Test
  fun `chaque ligne envoyee porte les finalites accordees`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    pipeline.submit(EventPipeline.Command.Track("purchase", null, mapOf("plan" to "pro"), nowMs))
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(List(3) { listOf("analytics", "marketing", "advertising") }, purposesOf(sender.batches.single()))
  }

  @Test
  fun `sous UNKNOWN les lignes sur disque restent nues et partent avec la reponse`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = ConsentState.UNKNOWN)
    track(pipeline)
    advanceUntilIdle()
    assertEquals(0, sender.batches.size)
    val onDisk = directory.walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
    assertEquals(false, onDisk.contains("purposes"))
    assertEquals(true, onDisk.contains("session_start"))
    setConsent(pipeline, ConsentState.GRANTED)
    advanceUntilIdle()
    assertEquals(List(3) { listOf("analytics", "marketing", "advertising") }, purposesOf(sender.batches.single()))
  }

  @Test
  fun `marketing refuse part avec les deux autres finalites`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    setConsent(pipeline, ConsentState.DENIED, ConsentPurpose.MARKETING)
    pipeline.submit(EventPipeline.Command.Track("purchase", null, null, nowMs))
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(listOf("analytics", "advertising"), purposesOf(sender.batches.single()).last())
  }

  @Test
  fun `les trois refusees vident la file et rien ne part`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = ConsentState.UNKNOWN)
    pipeline.submit(EventPipeline.Command.Track("purchase", null, null, nowMs))
    advanceUntilIdle()
    assertEquals(3, store.queuedEventCount)
    setConsent(pipeline, ConsentState.DENIED)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, store.queuedEventCount)
    assertEquals(0, sender.batches.size)
    assertEquals("1", prefs.getString("appwin.analytics.test.installTracked"))
  }

  @Test
  fun `une finalite inconnue retient le retard jusqu a la reponse`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    setConsent(pipeline, ConsentState.UNKNOWN, ConsentPurpose.ADVERTISING)
    pipeline.submit(EventPipeline.Command.Track("purchase", null, null, nowMs))
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, sender.batches.size)
    setConsent(pipeline, ConsentState.GRANTED, ConsentPurpose.ADVERTISING)
    advanceUntilIdle()
    assertEquals(listOf("analytics", "marketing", "advertising"), purposesOf(sender.batches.single()).last())
  }

  @Test
  fun `deux appels rapides, accorde puis refuse, finissent refuses sans rien envoyer`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = ConsentState.UNKNOWN)
    pipeline.submit(EventPipeline.Command.Track("purchase", null, null, nowMs))
    advanceUntilIdle()
    // Both writes land before either command runs.
    setConsent(pipeline, ConsentState.GRANTED)
    setConsent(pipeline, ConsentState.DENIED)
    advanceUntilIdle()
    assertEquals(0, sender.batches.size)
    assertEquals(0, store.queuedEventCount)
    assertTrue(consentStore.allDenied)
  }

  @Test
  fun `un signal sans changement dans le store ne touche a rien`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = ConsentState.UNKNOWN)
    pipeline.submit(EventPipeline.Command.Track("purchase", null, null, nowMs))
    pipeline.submit(EventPipeline.Command.ConsentChanged)
    advanceUntilIdle()
    assertEquals(3, store.queuedEventCount)
    assertEquals(0, sender.batches.size)
  }

  @Test
  fun `entre l ecriture et la commande, rien ne part ni n est mis en file`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    consentStore.set(ConsentState.DENIED, null)  // written, command not yet submitted
    pipeline.submit(EventPipeline.Command.Track("purchase", null, null, nowMs))
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(0, sender.batches.size)
    assertEquals(0, store.queuedEventCount)
  }

  @Test
  fun `trois refusees, le cycle de vie ne met rien en file et n ouvre pas de session`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = ConsentState.DENIED)
    pipeline.submit(EventPipeline.Command.Foreground)
    pipeline.submit(EventPipeline.Command.Background)
    runCurrent()
    pipeline.stop()
    assertEquals(0, store.queuedEventCount)
    assertEquals(0, sender.batches.size)
    assertEquals(null, prefs.getString("appwin.analytics.test.installTracked"))
  }

  @Test
  fun `refus puis accord des trois ne reemet pas app_install`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    track(pipeline)
    advanceUntilIdle()
    setConsent(pipeline, ConsentState.DENIED)
    advanceUntilIdle()
    setConsent(pipeline, ConsentState.GRANTED)
    track(pipeline)
    pipeline.submit(EventPipeline.Command.Flush)
    advanceUntilIdle()
    assertEquals(listOf("session_start", "spot_saved"), names(sender))
  }

  @Test
  fun `le premier plan vu pendant le refus donne app_open a l accord, sans doublon`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender, consent = ConsentState.DENIED)
    pipeline.submit(EventPipeline.Command.Foreground)
    pipeline.submit(EventPipeline.Command.Foreground)
    runCurrent()
    assertEquals(0, store.queuedEventCount)
    // The app is already in the foreground: no new Foreground is reported.
    setConsent(pipeline, ConsentState.GRANTED)
    runCurrent()
    pipeline.submit(EventPipeline.Command.Flush)
    runCurrent()
    pipeline.stop()
    assertEquals(listOf("app_install", "session_start", "app_open"), names(sender))
  }

  @Test
  fun `un arriere-plan pendant le refus remet app_open au prochain premier plan`() = runTest {
    val sender = FakeSender()
    val pipeline = makePipeline(sender)
    pipeline.submit(EventPipeline.Command.Foreground)
    runCurrent()
    setConsent(pipeline, ConsentState.DENIED)
    pipeline.submit(EventPipeline.Command.Background)
    runCurrent()
    setConsent(pipeline, ConsentState.GRANTED)
    pipeline.submit(EventPipeline.Command.Foreground)
    pipeline.submit(EventPipeline.Command.Flush)
    runCurrent()
    pipeline.stop()
    // The first period already left; the denial forgot the session, install is not re-emitted.
    assertEquals(
      listOf("app_install", "session_start", "app_open", "session_start", "app_open"),
      names(sender),
    )
  }
}
