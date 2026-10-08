package io.appwin.core

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.AppwinUnavailableReason
import kotlinx.coroutines.async
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
}
