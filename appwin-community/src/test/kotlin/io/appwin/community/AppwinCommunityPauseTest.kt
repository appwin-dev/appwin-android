package io.appwin.community

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinPause
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.push.AppwinPush
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(AppwinInternalApi::class)
class AppwinCommunityPauseTest {
  private lateinit var server: MockWebServer

  @Volatile
  private var verdict = """{"products":{"community":{"enabled":true,"debugOnly":true}}}"""

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    AppwinCore.resetForTesting()
    AppwinCommunity.resetForTesting()
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
    AppwinCommunity.resetForTesting()
    server.shutdown()
    context.applicationInfo.flags = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    AppwinCore.resetForTesting()
  }

  private fun idle() = shadowOf(Looper.getMainLooper()).idle()

  @Test
  fun `a ready Community that gets paused keeps its tab, loses its push and does not remount`() = runTest {
    val info = context.applicationInfo
    info.flags = info.flags or ApplicationInfo.FLAG_DEBUGGABLE
    val url = server.url("/").toString().trimEnd('/')
    AppwinCore.configure(context, "app-pause", baseUrl = url, realtimeBaseUrl = url)

    assertEquals(AppwinInitResult.Ready, AppwinCommunity.initialize())
    idle()
    assertNull(AppwinCommunity.pause)
    assertTrue(AppwinCommunity.debugUnlocked.value)
    assertTrue(AppwinPush.isRegistered("community"))

    val results = mutableListOf<AppwinInitResult>()
    val pauses = mutableListOf<AppwinPause?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AppwinCore.availabilityFlow(AppwinProduct.COMMUNITY).toList(results)
    }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AppwinCore.pauseFlow(AppwinProduct.COMMUNITY).toList(pauses)
    }
    yield()

    verdict = """{"products":{"community":{"enabled":false,"reason":"plan","paused":{"since":"2026-11-15T10:00:00.000Z","title":"T","message":"M"}}}}"""
    AppwinCore.availability(AppwinProduct.COMMUNITY)
    yield()
    idle()

    val pause = AppwinPause("2026-11-15T10:00:00.000Z", "T", "M")
    assertEquals(pause, AppwinCommunity.pause)
    assertTrue(AppwinCommunity.isReady)
    assertFalse(AppwinCommunity.debugUnlocked.value)
    assertFalse(AppwinPush.isRegistered("community"))
    assertEquals(listOf<AppwinInitResult>(AppwinInitResult.Ready), results)
    assertEquals(listOf<AppwinPause?>(null, pause), pauses)
  }
}
