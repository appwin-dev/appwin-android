package io.appwin.core.analytics

import io.appwin.core.network.ApiClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ApiEventSenderTest {
  private lateinit var server: MockWebServer
  private lateinit var sender: ApiEventSender

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    val client = ApiClient(
      baseUrl = server.url("/").toString().trimEnd('/'),
      headers = mapOf("Authorization" to "Bearer test"),
    )
    sender = ApiEventSender { client }
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private val lines = listOf(
    """{"eventId":"a","name":"session_start"}""",
    """{"eventId":"b","name":"spot_saved"}""",
  )

  @Test
  fun `un 200 envoie l'enveloppe du contrat et repond OK`() = runTest {
    server.enqueue(MockResponse().setBody("""{"accepted":2,"rejected":0}"""))
    assertEquals(SendOutcome.OK, sender.send(lines))
    val request = server.takeRequest()
    assertEquals("/api/sdk/v1/events", request.path)
    val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
    assertEquals(2, body["events"]!!.jsonArray.size)
    assertEquals("spot_saved", body["events"]!!.jsonArray[1].jsonObject["name"]!!.jsonPrimitive.content)
    assertNotNull(body["sentAt"])
  }

  @Test
  fun `un 200 avec quotaExceeded repond QUOTA_EXCEEDED`() = runTest {
    server.enqueue(MockResponse().setBody("""{"accepted":0,"rejected":2,"quotaExceeded":true}"""))
    assertEquals(SendOutcome.QUOTA_EXCEEDED, sender.send(lines))
  }

  @Test
  fun `un 401 repond UNAUTHORIZED`() = runTest {
    server.enqueue(MockResponse().setResponseCode(401))
    assertEquals(SendOutcome.UNAUTHORIZED, sender.send(lines))
  }

  @Test
  fun `un 400 repond FATAL`() = runTest {
    server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"invalid"}"""))
    assertEquals(SendOutcome.FATAL, sender.send(lines))
  }

  @Test
  fun `un 503 repond RETRYABLE`() = runTest {
    server.enqueue(MockResponse().setResponseCode(503))
    assertEquals(SendOutcome.RETRYABLE, sender.send(lines))
  }

  @Test
  fun `sans client configure la reponse est RETRYABLE`() = runTest {
    assertEquals(SendOutcome.RETRYABLE, ApiEventSender { null }.send(lines))
  }
}
