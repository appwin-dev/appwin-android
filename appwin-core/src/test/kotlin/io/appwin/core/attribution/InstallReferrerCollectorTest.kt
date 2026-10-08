package io.appwin.core.attribution

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import io.appwin.core.analytics.InMemoryAnalyticsPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstallReferrerCollectorTest {
  private lateinit var prefs: InMemoryAnalyticsPrefs
  private val emitted = ArrayList<Pair<String, Map<String, Any?>>>()

  @Before
  fun setUp() {
    prefs = InMemoryAnalyticsPrefs()
    emitted.clear()
  }

  private fun makeCollector(client: InstallReferrerClient): InstallReferrerCollector =
    InstallReferrerCollector(
      context = ApplicationProvider.getApplicationContext(),
      prefs = prefs,
      keyPrefix = PREFIX,
      emit = { name, props -> emitted.add(name to props) },
      clientFactory = { client },
    )

  private class FakeClient(
    private val responseCode: Int,
    private val details: ReferrerDetails? = null,
  ) : InstallReferrerClient() {
    var connections = 0
    var ended = false

    override fun startConnection(listener: InstallReferrerStateListener) {
      connections += 1
      listener.onInstallReferrerSetupFinished(responseCode)
    }

    override fun endConnection() {
      ended = true
    }

    override fun isReady(): Boolean = true

    override fun getInstallReferrer(): ReferrerDetails =
      details ?: throw IllegalStateException("no details")
  }

  private fun details(referrer: String): ReferrerDetails =
    ReferrerDetails(
      Bundle().apply {
        putString("install_referrer", referrer)
        putLong("referrer_click_timestamp_seconds", 1_700_000_000L)
        putLong("install_begin_timestamp_seconds", 1_700_000_100L)
        putBoolean("google_play_instant", false)
        putString("install_version", "1.2.3")
      },
    )

  @Test
  fun okResponseEmitsReservedEventAndBurnsTheShot() {
    val client = FakeClient(
      InstallReferrerClient.InstallReferrerResponse.OK,
      details("utm_source=tiktok&utm_campaign=parkeur_launch"),
    )
    makeCollector(client).start()

    assertEquals(1, emitted.size)
    val (name, props) = emitted[0]
    assertEquals("install_referrer", name)
    assertEquals("utm_source=tiktok&utm_campaign=parkeur_launch", props["referrer_url"])
    assertEquals(1_700_000_000L, props["referrer_click_ts"])
    assertEquals(1_700_000_100L, props["install_begin_ts"])
    assertEquals(false, props["google_play_instant"])
    assertEquals("1.2.3", props["install_version"])
    assertNotNull(prefs.getString(PREFIX + "referrer.tracked"))
    assertTrue(client.ended)
  }

  @Test
  fun secondStartDoesNothingOnceTracked() {
    val client = FakeClient(InstallReferrerClient.InstallReferrerResponse.OK, details("organic"))
    val collector = makeCollector(client)
    collector.start()
    collector.start()

    assertEquals(1, emitted.size)
    assertEquals(1, client.connections)
  }

  @Test
  fun serviceUnavailableLeavesTheShotForNextLaunch() {
    val client = FakeClient(InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE)
    makeCollector(client).start()

    assertEquals(0, emitted.size)
    assertNull(prefs.getString(PREFIX + "referrer.tracked"))
  }

  @Test
  fun featureNotSupportedBurnsTheShotWithoutEmitting() {
    val client = FakeClient(InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED)
    makeCollector(client).start()

    assertEquals(0, emitted.size)
    assertNotNull(prefs.getString(PREFIX + "referrer.tracked"))
  }

  @Test
  fun oversizedReferrerUrlIsTruncatedToTheWireCap() {
    val longReferrer = "utm_source=tiktok&payload=" + "x".repeat(400)
    val client = FakeClient(InstallReferrerClient.InstallReferrerResponse.OK, details(longReferrer))
    makeCollector(client).start()

    val url = emitted[0].second["referrer_url"] as String
    assertEquals(256, url.length)
    assertTrue(url.startsWith("utm_source=tiktok"))
  }

  private companion object {
    const val PREFIX = "appwin.analytics.test."
  }
}
