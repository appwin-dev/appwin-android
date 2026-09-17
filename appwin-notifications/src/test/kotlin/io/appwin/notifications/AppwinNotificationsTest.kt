package io.appwin.notifications

import androidx.test.core.app.ApplicationProvider
import io.appwin.core.AppwinCore
import io.appwin.core.network.AppwinApiException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppwinNotificationsTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.dispatcher = SessionAwareDispatcher()
    server.start()
    AppwinCore.configure(
      context = ApplicationProvider.getApplicationContext(),
      projectAppId = "app-123",
      baseUrl = server.url("/").toString().trimEnd('/'),
    )
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  /**
   * The next request the test is about, the session request skipped.
   *
   * `configure` is 100% local and opens no session: the first call needing a
   * bearer mints one, so `/auth/init` arrives in the middle of a test rather
   * than before it. Bounded on purpose - a request that never comes must fail
   * the test, not hang the build for want of one.
   */
  private fun nextRequest(): RecordedRequest {
    while (true) {
      val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) {
        "no request reached the server"
      }
      if (request.path != AUTH_INIT) return request
    }
  }

  @Test
  fun `registerPushToken poste le jeton avec la plateforme`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

    AppwinNotifications.registerPushToken("fcm-token", pushOptIn = false)

    val request = nextRequest()
    assertEquals("/api/sdk/support/v1/push-token", request.path)
    assertEquals("POST", request.method)

    val body = request.body.readUtf8()
    assertTrue(body.contains(""""token":"fcm-token""""))
    assertTrue(body.contains(""""platform":"android""""))
    assertTrue(body.contains(""""pushOptIn":false"""))
  }

  @Test
  fun `registerPushToken refuse un jeton vide`() = runTest {
    val error = runCatching { AppwinNotifications.registerPushToken("  ") }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `trackEvent envoie la valeur attendue par le serveur`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

    AppwinNotifications.trackEvent(AutomationEvent.APP_OPEN)

    val body = nextRequest().body.readUtf8()
    // `app_open`, not `APP_OPEN`: the server validates on this form.
    assertTrue(body.contains(""""event":"app_open""""))
  }

  @Test
  fun `fetchPendingMessages decode les messages`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """[{"id":"m1","campaignId":"c1","deliveryId":"d1","channel":"in_app",
           "content":{"title":"Salut","body":"Nouveauté"},"format":"modal"}]""",
      ),
    )

    val messages = AppwinNotifications.fetchPendingMessages()

    assertEquals(1, messages.size)
    assertEquals("m1", messages[0].id)
    assertEquals("Salut", messages[0].content.title)
  }

  @Test
  fun `un champ inconnu ne casse pas le decodage`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """[{"id":"m1","campaignId":"c1","deliveryId":"d1","channel":"in_app",
           "content":{"title":"Salut"},"format":"modal","futureField":42}]""",
      ),
    )

    // The server moves faster than the binaries installed on phones: an added
    // field must not break them.
    val messages = AppwinNotifications.fetchPendingMessages()
    assertEquals(1, messages.size)
  }

  @Test
  fun `syncOnAppOpen emet l evenement avant de lire`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

    AppwinNotifications.syncOnAppOpen()

    assertEquals("/api/sdk/notifications/v1/events", nextRequest().path)
    assertEquals("/api/sdk/notifications/v1/messages", nextRequest().path)
  }

  @Test
  fun `un refus serveur remonte le statut`() = runTest {
    server.enqueue(MockResponse().setResponseCode(403))

    val error = runCatching {
      AppwinNotifications.registerPushToken("fcm-token")
    }.exceptionOrNull()

    assertTrue(error is AppwinApiException.Http)
    assertEquals(403, (error as AppwinApiException.Http).status)
  }

  /**
   * Answers the session request out of band, and leaves the queue to the test.
   *
   * The session is minted lazily by the first call needing a bearer, and the
   * token then persists across tests in this JVM - so whether `/auth/init` is
   * requested at all depends on execution order. Queueing a response for it
   * made every test after the first read the session body as its own payload.
   */
  private class SessionAwareDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse =
      if (request.path == AUTH_INIT) {
        MockResponse()
          .setResponseCode(200)
          .setBody("""{"token":"tok-1","customerSessionId":"sess-1"}""")
      } else {
        super.dispatch(request)
      }
  }

  private companion object {
    const val AUTH_INIT = "/api/sdk/v1/auth/init"
  }
}
