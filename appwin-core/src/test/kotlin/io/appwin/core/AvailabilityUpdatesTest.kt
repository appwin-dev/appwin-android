package io.appwin.core

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinPause
import io.appwin.core.availability.AppwinProductState
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.AppwinUnavailableReason
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(AppwinInternalApi::class)
class AvailabilityUpdatesTest {
  private lateinit var server: MockWebServer

  @Volatile
  private var verdict = """{"products":{"community":{"enabled":true}}}"""

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    AppwinCore.resetForTesting()
    server = MockWebServer()
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse = when {
        request.path.orEmpty().contains("availability") ->
          MockResponse().setResponseCode(200).setBody(verdict)
        else ->
          MockResponse().setResponseCode(200).setBody("""{"token":"tok","customerSessionId":"s"}""")
      }
    }
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
    context.applicationInfo.flags = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    AppwinCore.resetForTesting()
  }

  private fun configure(debuggable: Boolean) {
    val info = context.applicationInfo
    info.flags = if (debuggable) {
      info.flags or ApplicationInfo.FLAG_DEBUGGABLE
    } else {
      info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }
    AppwinCore.configure(context, "app-avail", baseUrl = server.url("/").toString().trimEnd('/'))
  }

  @Test
  fun `a debug host marks its requests, a release host does not`() {
    configure(debuggable = true)
    assertEquals("debug", AppwinCore.canonicalHeaders()["X-Appwin-Build"])

    AppwinCore.resetForTesting()
    configure(debuggable = false)
    assertNull(AppwinCore.canonicalHeaders()["X-Appwin-Build"])
  }

  @Test
  fun `a debug-only verdict is ready and indistinguishable publicly`() = runTest {
    verdict = """{"products":{"community":{"enabled":true,"debugOnly":true}}}"""
    configure(debuggable = true)
    assertEquals(AppwinInitResult.Ready, AppwinCore.availability(AppwinProduct.COMMUNITY))
  }

  @Test
  fun `the stream emits the current verdict then each change`() = runTest {
    configure(debuggable = true)
    assertEquals(AppwinInitResult.Ready, AppwinCore.availabilityFlow(AppwinProduct.COMMUNITY).first())

    verdict = """{"products":{"community":{"enabled":false,"reason":"plan"}}}"""
    // Another product's initialize() refreshes the shared verdict for everyone.
    AppwinCore.availability(AppwinProduct.SUPPORT)
    assertEquals(
      AppwinInitResult.unavailable(AppwinUnavailableReason.PLAN),
      AppwinCore.availabilityFlow(AppwinProduct.COMMUNITY).first(),
    )
  }

  @Test
  fun `the stream does not repeat an unchanged verdict`() = runTest {
    configure(debuggable = true)
    AppwinCore.availability(AppwinProduct.COMMUNITY)
    AppwinCore.availability(AppwinProduct.COMMUNITY)
    verdict = """{"products":{}}"""
    val values = async {
      AppwinCore.availabilityFlow(AppwinProduct.COMMUNITY).take(2).toList()
    }
    yield()
    AppwinCore.availability(AppwinProduct.COMMUNITY)
    assertEquals(
      listOf(AppwinInitResult.Ready, AppwinInitResult.unavailable(AppwinUnavailableReason.DISABLED)),
      values.await(),
    )
  }

  @Test
  fun `without configure the stream says not configured`() = runTest {
    assertEquals(
      AppwinInitResult.NotConfigured,
      AppwinCore.availabilityFlow(AppwinProduct.COMMUNITY).first(),
    )
  }

  @Test
  fun `a paused product is ready and carries the pause`() = runTest {
    verdict = """{"products":{"support":{"enabled":false,"reason":"plan","paused":{"since":"2026-11-15T10:00:00.000Z","title":"On fait une pause","message":"Dites-lui de payer sa facture."}},"community":{"enabled":false,"reason":"plan"}}}"""
    configure(debuggable = true)
    assertEquals(AppwinInitResult.Ready, AppwinCore.availability(AppwinProduct.SUPPORT))
    assertEquals(
      AppwinPause("2026-11-15T10:00:00.000Z", "On fait une pause", "Dites-lui de payer sa facture."),
      AppwinCore.pause(AppwinProduct.SUPPORT),
    )
    assertEquals(AppwinInitResult.unavailable(AppwinUnavailableReason.PLAN), AppwinCore.availability(AppwinProduct.COMMUNITY))
    assertNull(AppwinCore.pause(AppwinProduct.COMMUNITY))
  }

  @Test
  fun `a pause without text is a pause with empty strings, a malformed one counts as out of plan`() = runTest {
    verdict = """{"products":{"support":{"enabled":false,"reason":"plan","paused":{"since":"2026-11-15T10:00:00.000Z"}}}}"""
    configure(debuggable = true)
    assertEquals(AppwinInitResult.Ready, AppwinCore.availability(AppwinProduct.SUPPORT))
    assertEquals("", AppwinCore.pause(AppwinProduct.SUPPORT)?.title)

    verdict = """{"products":{"support":{"enabled":false,"reason":"plan","paused":"yes"}}}"""
    assertEquals(AppwinInitResult.unavailable(AppwinUnavailableReason.PLAN), AppwinCore.availability(AppwinProduct.SUPPORT))
    assertNull(AppwinCore.pause(AppwinProduct.SUPPORT))
  }

  @Test
  fun `the availability request says the device language`() = runTest {
    val previous = java.util.Locale.getDefault()
    java.util.Locale.setDefault(java.util.Locale.CANADA_FRENCH)
    try {
      configure(debuggable = true)
      AppwinCore.availability(AppwinProduct.SUPPORT)
      val availability = generateSequence { server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }
        .first { it.path.orEmpty().contains("availability") }
      assertEquals("fr", availability.getHeader("X-Appwin-Language"))
    } finally {
      java.util.Locale.setDefault(previous)
    }
  }

  @Test
  fun `the pause flow emits when only the pause changes, the result flow stays quiet`() = runTest {
    verdict = """{"products":{"support":{"enabled":true}}}"""
    configure(debuggable = true)
    AppwinCore.availability(AppwinProduct.SUPPORT)
    val results = mutableListOf<AppwinInitResult>()
    val pauses = mutableListOf<AppwinPause?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AppwinCore.availabilityFlow(AppwinProduct.SUPPORT).toList(results)
    }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AppwinCore.pauseFlow(AppwinProduct.SUPPORT).toList(pauses)
    }
    yield()
    verdict = """{"products":{"support":{"enabled":false,"reason":"plan","paused":{"since":"2026-11-15T10:00:00.000Z","title":"T","message":"M"}}}}"""
    AppwinCore.availability(AppwinProduct.SUPPORT)
    yield()
    assertEquals(listOf<AppwinInitResult>(AppwinInitResult.Ready), results)
    assertEquals(listOf<AppwinPause?>(null, AppwinPause("2026-11-15T10:00:00.000Z", "T", "M")), pauses)
  }

  @Test
  fun `from out of plan to paused the state flow never shows ready without its pause`() = runTest {
    verdict = """{"products":{"community":{"enabled":false,"reason":"plan"}}}"""
    configure(debuggable = true)
    AppwinCore.availability(AppwinProduct.COMMUNITY)
    val states = mutableListOf<AppwinProductState>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AppwinCore.productStateFlow(AppwinProduct.COMMUNITY).toList(states)
    }
    yield()
    val pause = AppwinPause("2026-11-15T10:00:00.000Z", "T", "M")
    verdict = """{"products":{"community":{"enabled":false,"reason":"plan","paused":{"since":"2026-11-15T10:00:00.000Z","title":"T","message":"M"}}}}"""
    AppwinCore.availability(AppwinProduct.COMMUNITY)
    yield()
    assertEquals(
      listOf(
        AppwinProductState(AppwinInitResult.unavailable(AppwinUnavailableReason.PLAN), null),
        AppwinProductState(AppwinInitResult.Ready, pause),
      ),
      states,
    )
  }
}
