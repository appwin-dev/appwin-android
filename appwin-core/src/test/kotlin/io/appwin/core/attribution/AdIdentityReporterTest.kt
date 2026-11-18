package io.appwin.core.attribution

import androidx.test.core.app.ApplicationProvider
import io.appwin.core.analytics.InMemoryAnalyticsPrefs
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real dispatchers on purpose: ApiClient hops to Dispatchers.IO, which
 * a virtual-time scheduler cannot drive. Synchronisation goes through
 * MockWebServer.takeRequest and a bounded prefs poll instead.
 */
@RunWith(RobolectricTestRunner::class)
class AdIdentityReporterTest {
  private lateinit var prefs: InMemoryAnalyticsPrefs
  private lateinit var server: MockWebServer
  private lateinit var scope: CoroutineScope
  private var gaid: AdIdentityReporter.GaidInfo? =
    AdIdentityReporter.GaidInfo(id = GAID, limitAdTracking = false)

  @Before
  fun setUp() {
    prefs = InMemoryAnalyticsPrefs()
    server = MockWebServer()
    server.start()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }

  @After
  fun tearDown() {
    scope.cancel()
    server.shutdown()
  }

  private fun makeReporter(): AdIdentityReporter =
    AdIdentityReporter(
      context = ApplicationProvider.getApplicationContext(),
      prefs = prefs,
      keyPrefix = PREFIX,
      client = { ApiClient(server.url("/").toString().dropLast(1), emptyMap()) },
      reauthorize = { false },
      scope = scope,
      gaidProvider = { gaid },
    )

  private fun waitUntil(what: String, condition: () -> Boolean) {
    repeat(250) {
      if (condition()) return
      Thread.sleep(20)
    }
    throw AssertionError("timed out waiting for: $what")
  }

  @Test
  fun grantedConsentUploadsTheGaidOnce() {
    server.enqueue(MockResponse().setResponseCode(204))
    val reporter = makeReporter()
    reporter.setConsent(AdvertisingConsent.GRANTED)

    val request = server.takeRequest(5, TimeUnit.SECONDS)!!
    assertEquals("PUT", request.method)
    assertEquals("/api/sdk/v1/attribution/ad-identity", request.path)
    assertEquals("""{"platform":"android","adId":"$GAID"}""", request.body.readUtf8())
    waitUntil("sent marker") { prefs.getString(PREFIX + "adid.sent") == GAID }

    // Same value again: no second call.
    reporter.start()
    assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
  }

  @Test
  fun limitAdTrackingOverwritesWithANullIdentifier() {
    prefs.putString(PREFIX + "advertising.consent", "granted")
    prefs.putString(PREFIX + "adid.sent", GAID)
    gaid = AdIdentityReporter.GaidInfo(id = GAID, limitAdTracking = true)
    server.enqueue(MockResponse().setResponseCode(204))

    makeReporter().start()

    val request = server.takeRequest(5, TimeUnit.SECONDS)!!
    assertEquals("PUT", request.method)
    assertEquals("""{"platform":"android","adId":null}""", request.body.readUtf8())
    waitUntil("null marker") { prefs.getString(PREFIX + "adid.sent") == "-" }
  }

  @Test
  fun deniedConsentDeletesTheRemoteRow() {
    prefs.putString(PREFIX + "adid.sent", GAID)
    server.enqueue(MockResponse().setResponseCode(204))

    makeReporter().setConsent(AdvertisingConsent.DENIED)

    assertEquals("DELETE", server.takeRequest(5, TimeUnit.SECONDS)!!.method)
    waitUntil("cleared marker") { prefs.getString(PREFIX + "adid.sent") == null }
    assertEquals("denied", prefs.getString(PREFIX + "advertising.consent"))
  }

  @Test
  fun missingGmsReportsConsentWithANullIdentifier() {
    gaid = null
    server.enqueue(MockResponse().setResponseCode(204))
    makeReporter().setConsent(AdvertisingConsent.GRANTED)

    val request = server.takeRequest(5, TimeUnit.SECONDS)!!
    assertEquals("""{"platform":"android","adId":null}""", request.body.readUtf8())
    waitUntil("null marker") { prefs.getString(PREFIX + "adid.sent") == "-" }
  }

  @Test
  fun failedUploadRetriesAtNextStart() {
    server.enqueue(MockResponse().setResponseCode(500))
    server.enqueue(MockResponse().setResponseCode(204))
    val reporter = makeReporter()

    reporter.setConsent(AdvertisingConsent.GRANTED)
    assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
    waitUntil("first attempt settled") { server.requestCount == 1 }
    assertNull(prefs.getString(PREFIX + "adid.sent"))

    reporter.start()
    assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
    waitUntil("sent marker") { prefs.getString(PREFIX + "adid.sent") == GAID }
  }

  private companion object {
    const val PREFIX = "appwin.analytics.test."
    const val GAID = "12345678-90ab-cdef-1234-567890abcdef"
  }
}
