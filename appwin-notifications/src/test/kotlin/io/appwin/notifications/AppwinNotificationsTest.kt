package io.appwin.notifications

import androidx.test.core.app.ApplicationProvider
import io.appwin.core.AppwinCore
import io.appwin.core.network.AppwinApiException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppwinNotificationsTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    // The session is opened in the background by `configure`: this response
    // serves it, otherwise the test's first call would consume it.
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"token":"tok-1","customerSessionId":"sess-1"}"""),
    )
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

  /** Consumes the session-opening request `configure` fired. */
  private fun drainBootstrap() {
    server.takeRequest()
  }

  @Test
  fun `registerPushToken poste le jeton avec la plateforme`() = runTest {
    drainBootstrap()
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

    AppwinNotifications.registerPushToken("fcm-token", pushOptIn = false)

    val request = server.takeRequest()
    assertEquals("/api/sdk/notifications/v1/push-token", request.path)
    assertEquals("POST", request.method)

    val body = request.body.readUtf8()
    assertTrue(body.contains(""""token":"fcm-token""""))
    assertTrue(body.contains(""""platform":"android""""))
    assertTrue(body.contains(""""pushOptIn":false"""))
  }

  @Test
  fun `registerPushToken refuse un jeton vide`() = runTest {
    drainBootstrap()
    val error = runCatching { AppwinNotifications.registerPushToken("  ") }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `trackEvent envoie la valeur attendue par le serveur`() = runTest {
    drainBootstrap()
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

    AppwinNotifications.trackEvent(AutomationEvent.APP_OPEN)

    val body = server.takeRequest().body.readUtf8()
    // `app_open`, not `APP_OPEN`: the server validates on this form.
    assertTrue(body.contains(""""event":"app_open""""))
  }

  @Test
  fun `fetchPendingMessages decode les messages`() = runTest {
    drainBootstrap()
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
    drainBootstrap()
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
    drainBootstrap()
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

    AppwinNotifications.syncOnAppOpen()

    assertEquals("/api/sdk/notifications/v1/events", server.takeRequest().path)
    assertEquals("/api/sdk/notifications/v1/messages", server.takeRequest().path)
  }

  @Test
  fun `un refus serveur remonte le statut`() = runTest {
    drainBootstrap()
    server.enqueue(MockResponse().setResponseCode(403))

    val error = runCatching {
      AppwinNotifications.registerPushToken("fcm-token")
    }.exceptionOrNull()

    assertTrue(error is AppwinApiException.Http)
    assertEquals(403, (error as AppwinApiException.Http).status)
  }
}
