@file:OptIn(AppwinInternalApi::class)

package io.appwin.core.analytics.crash

import io.appwin.core.AppwinInternalApi
import io.appwin.core.analytics.AnalyticsConsent
import io.appwin.core.analytics.SendOutcome
import io.appwin.core.network.ApiClient
import com.facebook.react.common.JavascriptException
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrashReporterTest {
  private lateinit var directory: File
  private lateinit var store: CrashStore
  private val sender = FakeSender()
  private var consent = AnalyticsConsent.GRANTED

  private class FakeSender : CrashSender {
    val batches = mutableListOf<List<String>>()
    val outcomes = ArrayDeque<SendOutcome>()

    override suspend fun send(reports: List<String>): SendOutcome {
      batches.add(reports)
      return outcomes.removeFirstOrNull() ?: SendOutcome.OK
    }
  }

  @Before
  fun setUp() {
    directory = File.createTempFile("appwin-crash-reporter", "").apply {
      delete()
      mkdirs()
    }
    store = CrashStore(directory)
  }

  @After
  fun tearDown() {
    directory.deleteRecursively()
  }

  private fun TestScope.reporter(
    breadcrumbs: Breadcrumbs = Breadcrumbs(),
    scope: TestScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
    now: () -> Long = System::currentTimeMillis,
  ) = CrashReporter(
    store = store,
    sender = sender,
    breadcrumbs = breadcrumbs,
    classifier = InAppClassifier(listOf("io.appwin.core.analytics.crash")),
    crashContext = CrashContext("1.0.0", "1", "Android 15", "Google Pixel", "0.9.2"),
    storedConsent = { consent },
    sessionId = { "0190f5d2-1c3a-7b4e-9f00-000000000002" },
    reauthorize = { true },
    scope = scope,
    now = now,
  )

  @Test
  fun `a crash is written with its session, screen and breadcrumbs`() = runTest {
    val breadcrumbs = Breadcrumbs()
    breadcrumbs.screen("home")
    breadcrumbs.event("cart_opened")
    reporter(breadcrumbs).onUncaught(IllegalStateException("boom"))

    val json = Json.parseToJsonElement(store.pending(10).single().json).jsonObject
    assertEquals("crash", json["kind"]!!.jsonPrimitive.content)
    assertEquals("home", json["screen"]!!.jsonPrimitive.content)
    assertNotNull(json["sessionId"])
    assertEquals(2, json["breadcrumbs"]!!.jsonArray.size)
    assertEquals(
      "java.lang.IllegalStateException",
      json["exception"]!!.jsonObject["type"]!!.jsonPrimitive.content,
    )
  }

  @Test
  fun `a wrapped crash is reported as its root cause`() = runTest {
    val root = IllegalStateException("root")
    root.stackTrace = arrayOf(StackTraceElement("com.acme.app.Repo", "load", "Repo.kt", 31))
    val wrapper = RuntimeException("Unable to start activity", root)
    wrapper.stackTrace = arrayOf(StackTraceElement("android.app.ActivityThread", "main", null, 1))
    reporter().onUncaught(wrapper)

    val json = Json.parseToJsonElement(store.pending(10).single().json).jsonObject
    val exception = json["exception"]!!.jsonObject
    assertEquals("java.lang.IllegalStateException", exception["type"]!!.jsonPrimitive.content)
    assertEquals("root", exception["message"]!!.jsonPrimitive.content)
    val frames = json["frames"]!!.jsonArray
    assertEquals(1, frames.size)
    assertEquals("com.acme.app.Repo.load", frames[0].jsonObject["fn"]!!.jsonPrimitive.content)
  }

  @Test
  fun `a root cause without message falls back to the top-level one`() = runTest {
    reporter().recordError(RuntimeException("outer", IllegalStateException()))
    val sent = Json.parseToJsonElement(sender.batches.single().single()).jsonObject
    assertEquals("outer", sent["exception"]!!.jsonObject["message"]!!.jsonPrimitive.content)
  }

  @Test
  fun `consent denied writes nothing`() = runTest {
    consent = AnalyticsConsent.DENIED
    val reporter = reporter()
    reporter.onUncaught(RuntimeException("boom"))
    reporter.recordError(RuntimeException("caught"))
    assertEquals(0, store.count())
  }

  @Test
  fun `consent denied at runtime purges the backlog`() = runTest {
    val reporter = reporter()
    consent = AnalyticsConsent.UNKNOWN
    reporter.onUncaught(RuntimeException("boom"))
    reporter.onConsentChanged(AnalyticsConsent.DENIED)
    assertEquals(0, store.count())
  }

  @Test
  fun `consent unknown stores but never sends`() = runTest {
    consent = AnalyticsConsent.UNKNOWN
    val reporter = reporter()
    reporter.recordError(RuntimeException("caught"))
    reporter.flushNow()
    assertEquals(1, store.count())
    assertTrue(sender.batches.isEmpty())
  }

  @Test
  fun `granting consent sends the backlog without waiting for the pipeline`() = runTest {
    consent = AnalyticsConsent.UNKNOWN
    val reporter = reporter()
    reporter.onUncaught(RuntimeException("boom"))
    reporter.onConsentChanged(AnalyticsConsent.GRANTED)
    assertEquals(1, sender.batches.size)
    assertEquals(0, store.count())
  }

  @Test
  fun `a non-fatal is persisted then sent`() = runTest {
    reporter().recordError(RuntimeException("caught"))
    val sent = Json.parseToJsonElement(sender.batches.single().single()).jsonObject
    assertEquals("non_fatal", sent["kind"]!!.jsonPrimitive.content)
    assertEquals(0, store.count())
  }

  @Test
  fun `a retryable failure keeps the reports, a fatal one drops them`() = runTest {
    val reporter = reporter()
    consent = AnalyticsConsent.UNKNOWN
    reporter.onUncaught(RuntimeException("boom"))
    consent = AnalyticsConsent.GRANTED

    sender.outcomes.add(SendOutcome.RETRYABLE)
    reporter.flushNow()
    assertEquals(1, store.count())

    sender.outcomes.add(SendOutcome.FATAL)
    reporter.flushNow()
    assertEquals(0, store.count())
  }

  @Test
  fun `a bridged error is on disk before the call returns`() = runTest {
    // A dispatcher that never runs on its own: only a synchronous write can be seen.
    val parked = TestScope(StandardTestDispatcher())
    val breadcrumbs = Breadcrumbs()
    breadcrumbs.screen("checkout")
    val stored = reporter(breadcrumbs, parked).recordBridged(
      "react_native", fatal = true, type = "TypeError", message = "x is undefined", frames = emptyList(),
    )

    assertTrue(stored)
    assertTrue(sender.batches.isEmpty())
    val json = Json.parseToJsonElement(store.pending(10).single().json).jsonObject
    assertEquals("react_native", json["runtime"]!!.jsonPrimitive.content)
    assertEquals("crash", json["kind"]!!.jsonPrimitive.content)
    assertEquals("checkout", json["screen"]!!.jsonPrimitive.content)
    assertNotNull(json["sessionId"])
    assertEquals("1.0.0", json["app"]!!.jsonObject["version"]!!.jsonPrimitive.content)
    assertEquals("Google Pixel", json["device"]!!.jsonObject["model"]!!.jsonPrimitive.content)
    assertEquals("TypeError", json["exception"]!!.jsonObject["type"]!!.jsonPrimitive.content)
  }

  @Test
  fun `a bridged non-fatal is persisted then sent`() = runTest {
    reporter().recordBridged("flutter", fatal = false, type = "StateError", message = null, frames = emptyList())
    val sent = Json.parseToJsonElement(sender.batches.single().single()).jsonObject
    assertEquals("non_fatal", sent["kind"]!!.jsonPrimitive.content)
    assertEquals("flutter", sent["runtime"]!!.jsonPrimitive.content)
    assertNull(sent["exception"]!!.jsonObject["message"])
    assertEquals(0, store.count())
  }

  @Test
  fun `a bridged error from an unknown runtime is ignored`() = runTest {
    val reporter = reporter()
    for (runtime in listOf("android", "ios", "web", "Flutter", "")) {
      assertFalse(reporter.recordBridged(runtime, fatal = true, type = "E", message = null, frames = emptyList()))
    }
    assertEquals(0, store.count())
    assertTrue(sender.batches.isEmpty())
  }

  @Test
  fun `a bridged error under denied consent writes nothing`() = runTest {
    consent = AnalyticsConsent.DENIED
    assertFalse(reporter().recordBridged("flutter", fatal = true, type = "E", message = null, frames = emptyList()))
    assertEquals(0, store.count())
  }

  @Test
  fun `bridged frames accept any number type and keep the contract bounds`() = runTest {
    val frames = listOf(
      mapOf("fn" to "main", "file" to "package:app/main.dart", "line" to 12, "col" to 7L, "module" to "package:app", "inApp" to true),
      mapOf("fn" to "render", "line" to 40.0, "col" to 3.9),
      mapOf("fn" to "x".repeat(600), "line" to -1, "col" to Double.NaN, "inApp" to "yes"),
      mapOf("line" to 1e12, "file" to 42),
    ) + List(250) { mapOf("fn" to "f$it") }
    reporter().recordBridged(
      "flutter", fatal = false, type = "T".repeat(300), message = "m".repeat(2000), frames = frames,
    )

    val sent = Json.parseToJsonElement(sender.batches.single().single()).jsonObject
    val exception = sent["exception"]!!.jsonObject
    assertEquals(256, exception["type"]!!.jsonPrimitive.content.length)
    assertEquals(CrashReport.MAX_MESSAGE, exception["message"]!!.jsonPrimitive.content.length)
    val out = sent["frames"]!!.jsonArray.map { it.jsonObject }
    assertEquals(CrashReport.MAX_FRAMES, out.size)

    assertEquals(12, out[0]["line"]!!.jsonPrimitive.int)
    assertEquals(7, out[0]["col"]!!.jsonPrimitive.int)
    assertEquals("package:app", out[0]["module"]!!.jsonPrimitive.content)
    assertTrue(out[0]["inApp"]!!.jsonPrimitive.boolean)

    assertEquals(40, out[1]["line"]!!.jsonPrimitive.int)
    assertEquals(3, out[1]["col"]!!.jsonPrimitive.int)
    assertFalse(out[1]["inApp"]!!.jsonPrimitive.boolean)

    assertEquals(512, out[2]["fn"]!!.jsonPrimitive.content.length)
    assertNull(out[2]["line"])
    assertNull(out[2]["col"])
    assertFalse(out[2]["inApp"]!!.jsonPrimitive.boolean)

    assertEquals("", out[3]["fn"]!!.jsonPrimitive.content)
    assertNull(out[3]["line"])
    assertNull(out[3]["file"])
  }

  @Test
  fun `a react native fatal is not reported twice`() = runTest {
    var clock = 1_000_000L
    val parked = TestScope(StandardTestDispatcher())
    val reporter = reporter(scope = parked, now = { clock })
    reporter.recordBridged("react_native", fatal = true, type = "TypeError", message = "boom", frames = emptyList())

    clock += 9_000
    reporter.onUncaught(RuntimeException("wrapper", JavascriptException("boom")))

    val reports = store.pending(10)
    assertEquals(1, reports.size)
    assertEquals("react_native", Json.parseToJsonElement(reports.single().json).jsonObject["runtime"]!!.jsonPrimitive.content)
  }

  @Test
  fun `a javascript exception is still reported outside the bridged fatal window`() = runTest {
    var clock = 1_000_000L
    val parked = TestScope(StandardTestDispatcher())
    val reporter = reporter(scope = parked, now = { clock })

    reporter.onUncaught(JavascriptException("no bridged fatal before"))
    assertEquals(1, store.count())

    reporter.recordBridged("react_native", fatal = false, type = "E", message = null, frames = emptyList())
    reporter.onUncaught(JavascriptException("after a non-fatal"))
    assertEquals(3, store.count())

    reporter.recordBridged("react_native", fatal = true, type = "E", message = null, frames = emptyList())
    clock += 10_001
    reporter.onUncaught(JavascriptException("too late"))
    assertEquals(5, store.count())
  }

  @Test
  fun `only one echo is skipped and other crashes are kept`() = runTest {
    val parked = TestScope(StandardTestDispatcher())
    val reporter = reporter(scope = parked)
    reporter.recordBridged("react_native", fatal = true, type = "E", message = null, frames = emptyList())

    reporter.onUncaught(IllegalStateException("native bug"))
    assertEquals(2, store.count())
    reporter.onUncaught(JavascriptException("echo"))
    assertEquals(2, store.count())
    reporter.onUncaught(JavascriptException("a later one"))
    assertEquals(3, store.count())
  }

  @Test
  fun `posts the crash envelope to the crashes route`() = runTest {
    val server = MockWebServer()
    server.start()
    try {
      server.enqueue(MockResponse().setBody("""{"accepted":1,"rejected":0}"""))
      val client = ApiClient(baseUrl = server.url("/").toString().trimEnd('/'), headers = emptyMap())
      val outcome = ApiCrashSender { client }.send(listOf("""{"crashId":"a"}"""))
      assertEquals(SendOutcome.OK, outcome)
      val request = server.takeRequest()
      assertEquals("/api/sdk/v1/crashes", request.path)
      val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
      assertEquals(1, body["crashes"]!!.jsonArray.size)
      assertNotNull(body["sentAt"])
    } finally {
      server.shutdown()
    }
  }
}
