@file:OptIn(AppwinInternalApi::class)

package io.appwin.core

import androidx.test.core.app.ApplicationProvider
import io.appwin.core.network.AppwinApiException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppwinCoreTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    AppwinCore.resetForTesting()
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
    AppwinCore.resetForTesting()
  }

  private fun configure() {
    AppwinCore.configure(
      context = ApplicationProvider.getApplicationContext(),
      projectAppId = "app-123",
      baseUrl = server.url("/").toString().trimEnd('/'),
    )
  }

  @Test
  fun `configure genere un identifiant d appareil et le reutilise`() {
    configure()
    val first = AppwinCore.deviceId
    assertNotNull(first)

    AppwinCore.resetForTesting()
    configure()

    // The second start must find the same device, otherwise every launch would
    // create a new anonymous profile.
    assertEquals(first, AppwinCore.deviceId)
  }

  @Test
  fun `configure refuse un app id vide`() {
    val error = runCatching {
      AppwinCore.configure(ApplicationProvider.getApplicationContext(), " ")
    }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `les en-tetes canoniques portent l identite`() {
    configure()
    val headers = AppwinCore.canonicalHeaders()

    assertEquals("android", headers["X-Appwin-Platform"])
    assertEquals("app-123", headers["X-Appwin-App-Id"])
    assertNotNull(headers["X-Appwin-Device-Id"])
    assertNull(headers["X-Appwin-User-Id"])
  }

  @Test
  fun `bootstrapSession pose le jeton dans les en-tetes`() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"token":"tok-1","customerSessionId":"sess-1"}"""),
    )
    configure()

    val token = AppwinCore.bootstrapSession()

    assertEquals("tok-1", token)
    assertEquals("Bearer tok-1", AppwinCore.canonicalHeaders()["Authorization"])
  }

  @Test
  fun `les bootstraps concurrents partagent un seul appel`() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"token":"tok-1","customerSessionId":"sess-1"}"""),
    )
    configure()

    // `/auth/init` rotates the token: two real calls would revoke each other's.
    // Only one must go out.
    val tokens = listOf(
      async { AppwinCore.bootstrapSession() },
      async { AppwinCore.bootstrapSession() },
      async { AppwinCore.bootstrapSession() },
    ).awaitAll()

    assertEquals(listOf("tok-1", "tok-1", "tok-1"), tokens)
  }

  @Test
  fun `bootstrapSession sans configure leve NotConfigured`() = runTest {
    val error = runCatching { AppwinCore.bootstrapSession() }.exceptionOrNull()
    assertTrue(error is AppwinApiException.NotConfigured)
  }

  @Test
  fun `une erreur serveur remonte le statut`() = runTest {
    server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"nope"}"""))
    configure()

    val error = runCatching { AppwinCore.bootstrapSession() }.exceptionOrNull()

    assertTrue(error is AppwinApiException.Http)
    assertEquals(401, (error as AppwinApiException.Http).status)
  }

  @Test
  fun `registerPushToken poste le jeton via la route support`() = runTest {
    // `configure` opens the session in the background, and that request is
    // served first: queueing only the push-token answer left the session
    // holding it, and the token call then waited for a reply that never came.
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"token":"tok-1","customerSessionId":"sess-1"}"""),
    )
    configure()
    AppwinCore.bootstrapSession()

    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"ok":true}"""),
    )
    AppwinCore.registerPushToken("fcm-token", pushOptIn = false)

    server.takeRequest()
    val request = server.takeRequest()
    assertEquals("/api/sdk/support/v1/push-token", request.path)
    assertTrue(request.body.readUtf8().contains("fcm-token"))
    assertTrue(AppwinCore.hasRegisteredPushToken)
  }

  @Test
  fun `registerPushToken refuse un jeton vide`() = runTest {
    configure()
    val error = runCatching { AppwinCore.registerPushToken("  ") }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `logout clears the token even when the revoke fails`() = runTest {
    server.enqueue(session("tok-1"))
    configure()
    AppwinCore.bootstrapSession()

    server.enqueue(MockResponse().setResponseCode(500))
    server.enqueue(MockResponse().setResponseCode(500))
    AppwinCore.logout()

    assertNull(AppwinCore.canonicalHeaders()["Authorization"])
  }

  @Test
  fun `identify sends the externalId to auth init and persists it`() = runTest {
    server.enqueue(session("tok-1"))
    configure()

    AppwinCore.identify("user-42")

    val init = server.takeRequest()
    assertEquals("/api/sdk/v1/auth/init", init.path)
    assertTrue(init.body.readUtf8().contains(""""externalId":"user-42""""))
    assertEquals("user-42", AppwinCore.canonicalHeaders()["X-Appwin-User-Id"])

    // A relaunch must come back identified, not anonymous.
    AppwinCore.resetForTesting()
    configure()
    assertEquals("user-42", AppwinCore.externalId)
  }

  @Test
  fun `a later bootstrap keeps the stored externalId`() = runTest {
    server.enqueue(session("tok-1"))
    server.enqueue(session("tok-2"))
    configure()
    AppwinCore.identify("user-42")
    server.takeRequest()

    AppwinCore.bootstrapSession()

    assertTrue(server.takeRequest().body.readUtf8().contains(""""externalId":"user-42""""))
  }

  @Test
  fun `identify rejects a blank externalId`() = runTest {
    configure()
    val error = runCatching { AppwinCore.identify(" ") }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `identify with attributes patches me after opening the session`() = runTest {
    server.enqueue(session("tok-1"))
    server.enqueue(MockResponse().setResponseCode(204))
    configure()

    AppwinCore.identify("user-42", AppwinUserAttributes(email = "ada@example.com"))

    assertEquals("/api/sdk/v1/auth/init", server.takeRequest().path)
    val patch = server.takeRequest()
    assertEquals("/api/sdk/v1/me", patch.path)
    assertEquals("PATCH", patch.method)
    assertEquals("Bearer tok-1", patch.getHeader("Authorization"))
    // Omitted attributes must not reach the server as nulls: they would erase data.
    assertEquals("""{"email":"ada@example.com"}""", patch.body.readUtf8())
  }

  @Test
  fun `updateUser opens a session first when there is none`() = runTest {
    server.enqueue(session("tok-1"))
    server.enqueue(MockResponse().setResponseCode(204))
    configure()

    AppwinCore.updateUser(AppwinUserAttributes(plan = "pro"))

    assertEquals("/api/sdk/v1/auth/init", server.takeRequest().path)
    assertEquals("/api/sdk/v1/me", server.takeRequest().path)
  }

  @Test
  fun `identity changes are announced to the modules`() = runTest {
    server.enqueue(session("tok-1"))
    server.enqueue(MockResponse().setResponseCode(204))
    configure()
    val seen = async(start = CoroutineStart.UNDISPATCHED) {
      AppwinCore.identityChanges.first()
    }

    AppwinCore.updateUser(AppwinUserAttributes(name = "Ada"))

    assertEquals(Unit, seen.await())
  }

  @Test
  fun `logout goes anonymous and registers the push token again`() = runTest {
    server.enqueue(session("tok-1"))
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    configure()
    AppwinCore.identify("user-42")
    AppwinCore.registerPushToken("fcm-token")
    server.takeRequest()
    server.takeRequest()

    server.enqueue(MockResponse().setResponseCode(204))
    server.enqueue(session("tok-2"))
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    AppwinCore.logout()

    assertEquals("/api/sdk/v1/auth/revoke", server.takeRequest().path)
    val init = server.takeRequest()
    assertEquals("/api/sdk/v1/auth/init", init.path)
    assertTrue(!init.body.readUtf8().contains("externalId"))
    val push = server.takeRequest()
    assertEquals("/api/sdk/support/v1/push-token", push.path)
    assertEquals("Bearer tok-2", push.getHeader("Authorization"))
    assertNull(AppwinCore.externalId)

    AppwinCore.resetForTesting()
    configure()
    assertNull(AppwinCore.externalId)
  }

  private fun session(token: String) =
    MockResponse()
      .setResponseCode(200)
      .setBody("""{"token":"$token","customerSessionId":"sess-1"}""")
}
