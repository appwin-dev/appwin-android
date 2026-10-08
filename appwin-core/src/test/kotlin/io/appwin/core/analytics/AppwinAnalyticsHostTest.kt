@file:OptIn(AppwinInternalApi::class)

package io.appwin.core.analytics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.AppwinUnavailableReason
import io.appwin.core.network.MultipartFile
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppwinAnalyticsHostTest {
  private lateinit var server: MockWebServer
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Volatile
  private var verdict = """{"products":{"analytics":{"enabled":true}}}"""

  /** Status codes the segment route answers with, in order; 200 once exhausted. */
  private val segmentStatuses = ConcurrentLinkedQueue<Int>()
  private val segmentRequests = ConcurrentLinkedQueue<RecordedRequest>()

  @Before
  fun setUp() {
    AppwinCore.resetForTesting()
    server = MockWebServer()
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty()
        return when {
          path.contains("availability") -> MockResponse().setBody(verdict)
          path.contains("replays/segments") -> {
            segmentRequests.add(request)
            val status = segmentStatuses.poll() ?: 200
            MockResponse().setResponseCode(status).setBody("""{"accepted":true}""")
          }
          else -> MockResponse().setBody("""{"token":"tok","customerSessionId":"s"}""")
        }
      }
    }
    server.start()
    AppwinCore.configure(context, "app-replay", baseUrl = server.url("/").toString().trimEnd('/'))
  }

  @After
  fun tearDown() {
    server.shutdown()
    AppwinCore.resetForTesting()
  }

  private fun segmentFile(): MultipartFile {
    val file = File(context.cacheDir, "segment.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    return MultipartFile("segment", "segment.mp4", "video/mp4", file)
  }

  private suspend fun post() = AppwinAnalyticsHost.postMultipart(
    "/api/sdk/v1/replays/segments",
    mapOf("meta" to """{"seq":0}"""),
    segmentFile(),
  )

  @Test
  fun `the replay verdict carries its config, read without the network`() = runTest {
    verdict = """{"products":{"replay":{"enabled":true,"config":{"sampleRate":0.5,"maskAllText":false}}}}"""
    assertEquals(AppwinInitResult.Ready, AppwinCore.availability(AppwinProduct.REPLAY))
    val config = AppwinAnalyticsHost.productConfig(AppwinProduct.REPLAY)
    assertEquals("0.5", config?.get("sampleRate")?.jsonPrimitive?.content)
    assertNull(AppwinAnalyticsHost.productConfig(AppwinProduct.ANALYTICS))
  }

  @Test
  fun `a verdict without replay means switched off`() = runTest {
    assertEquals(
      AppwinInitResult.unavailable(AppwinUnavailableReason.DISABLED),
      AppwinCore.availability(AppwinProduct.REPLAY),
    )
  }

  @Test
  fun `the segment goes out as multipart with the SDK headers`() = runTest {
    assertEquals(SendOutcome.OK, post())
    val request = segmentRequests.single()
    assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
    assertEquals("android", request.getHeader("X-Appwin-Platform"))
    val body = request.body.readUtf8()
    assertTrue(body.contains("name=\"meta\""))
    assertTrue(body.contains("{\"seq\":0}"))
    assertTrue(body.contains("name=\"segment\"; filename=\"segment.mp4\""))
    assertTrue(body.contains("Content-Type: video/mp4"))
  }

  @Test
  fun `a 401 is left to the uploader, which can re-bootstrap`() = runTest {
    segmentStatuses.add(401)
    assertEquals(SendOutcome.UNAUTHORIZED, post())
    assertTrue(AppwinAnalyticsHost.reauthorize())
    assertEquals(SendOutcome.OK, post())
    assertEquals(2, segmentRequests.size)
  }

  @Test
  fun `statuses map to what the replay queue must do`() = runTest {
    segmentStatuses.addAll(listOf(403, 413, 400, 429, 503))
    assertEquals(SendOutcome.FORBIDDEN, post())
    assertEquals(SendOutcome.FATAL, post())
    assertEquals(SendOutcome.FATAL, post())
    assertEquals(SendOutcome.RETRYABLE, post())
    assertEquals(SendOutcome.RETRYABLE, post())
  }
}
