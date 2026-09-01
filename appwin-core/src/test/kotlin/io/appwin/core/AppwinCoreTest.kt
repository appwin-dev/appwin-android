package io.appwin.core

import androidx.test.core.app.ApplicationProvider
import io.appwin.core.network.AppwinApiException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    AppwinCore.identify("user-42")
    assertEquals("user-42", AppwinCore.canonicalHeaders()["X-Appwin-User-Id"])

    AppwinCore.clearIdentity()
    assertNull(AppwinCore.canonicalHeaders()["X-Appwin-User-Id"])
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
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"ok":true}"""),
    )
    configure()

    AppwinCore.registerPushToken("fcm-token", pushOptIn = false)

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
  fun `signOut vide le jeton meme si la revocation echoue`() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"token":"tok-1","customerSessionId":"sess-1"}"""),
    )
    configure()
    AppwinCore.bootstrapSession()

    server.enqueue(MockResponse().setResponseCode(500))
    AppwinCore.signOut()

    assertNull(AppwinCore.canonicalHeaders()["Authorization"])
  }
}
