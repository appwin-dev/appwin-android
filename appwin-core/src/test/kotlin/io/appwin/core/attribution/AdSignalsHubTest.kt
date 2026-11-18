package io.appwin.core.attribution

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.appwin.core.network.ApiClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdSignalsHubTest {
  private class FakeAdapter(override val network: String) : AdSignalsAdapter {
    var activations = 0
    var deactivations = 0
    var config: Map<String, String>? = null
    val events = ArrayList<Triple<String, String, Map<String, Any?>?>>()

    override fun activate(context: Context, config: Map<String, String>) {
      activations += 1
      this.config = config
    }

    override fun deactivate() {
      deactivations += 1
    }

    override fun onEvent(name: String, eventId: String, props: Map<String, Any?>?) {
      events.add(Triple(name, eventId, props))
    }
  }

  private lateinit var server: MockWebServer
  private lateinit var scope: CoroutineScope
  private var consent = AdvertisingConsent.GRANTED

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    consent = AdvertisingConsent.GRANTED
  }

  @After
  fun tearDown() {
    scope.cancel()
    server.shutdown()
  }

  private fun makeHub(adapter: FakeAdapter, debugMode: Boolean = false): AdSignalsHub =
    AdSignalsHub(
      context = ApplicationProvider.getApplicationContext(),
      client = { ApiClient(server.url("/").toString().dropLast(1), emptyMap()) },
      consent = { consent },
      scope = scope,
      debugMode = { debugMode },
      adapters = listOf(adapter),
    )

  private fun waitUntil(what: String, condition: () -> Boolean) {
    repeat(250) {
      if (condition()) return
      Thread.sleep(20)
    }
    throw AssertionError("timed out waiting for: $what")
  }

  @Test
  fun activatesAWiredNetworkUnderGrantedConsent() {
    server.enqueue(
      MockResponse().setBody("""{"networks":{"tiktok":{"appId":"7412345678901234567"}}}"""),
    )
    val adapter = FakeAdapter("tiktok")
    val hub = makeHub(adapter)

    hub.start()
    waitUntil("activation") { adapter.activations == 1 }
    assertEquals("7412345678901234567", adapter.config?.get("appId"))

    hub.onEvent("purchase", "evt-1", mapOf("plan" to "pro"))
    assertEquals(listOf(Triple("purchase", "evt-1", mapOf<String, Any?>("plan" to "pro"))), adapter.events)
  }

  @Test
  fun debugModeReachesTheAdapterConfig() {
    server.enqueue(
      MockResponse().setBody("""{"networks":{"tiktok":{"appId":"1"}}}"""),
    )
    val adapter = FakeAdapter("tiktok")
    val hub = makeHub(adapter, debugMode = true)

    hub.start()
    waitUntil("activation") { adapter.activations == 1 }
    assertEquals("true", adapter.config?.get("debugMode"))
  }

  @Test
  fun unwiredNetworkNeverActivates() {
    server.enqueue(MockResponse().setBody("""{"networks":{}}"""))
    val adapter = FakeAdapter("tiktok")
    val hub = makeHub(adapter)

    hub.start()
    // The config fetch resolves (one request served), then nothing runs.
    assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
    waitUntil("settled") { server.requestCount == 1 }
    assertEquals(0, adapter.activations)
    hub.onEvent("purchase", "evt-1", null)
    assertEquals(0, adapter.events.size)
  }

  @Test
  fun consentWithdrawalDeactivates() {
    server.enqueue(
      MockResponse().setBody("""{"networks":{"tiktok":{"appId":"1"}}}"""),
    )
    val adapter = FakeAdapter("tiktok")
    val hub = makeHub(adapter)
    hub.start()
    waitUntil("activation") { adapter.activations == 1 }

    consent = AdvertisingConsent.DENIED
    hub.onConsentChanged()
    waitUntil("deactivation") { adapter.deactivations == 1 }
    hub.onEvent("purchase", "evt-1", null)
    assertEquals(0, adapter.events.size)
  }

  @Test
  fun unreachableConfigDegradesToNothingActive() {
    server.shutdown()
    val adapter = FakeAdapter("tiktok")
    val hub = makeHub(adapter)
    hub.start()

    Thread.sleep(200)
    assertEquals(0, adapter.activations)
  }
}
