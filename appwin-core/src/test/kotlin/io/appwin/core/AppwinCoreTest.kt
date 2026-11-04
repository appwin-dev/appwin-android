@file:OptIn(AppwinInternalApi::class)

package io.appwin.core

import androidx.test.core.app.ApplicationProvider
import io.appwin.core.analytics.AnalyticsRuntime
import io.appwin.core.analytics.SharedPrefsAnalyticsPrefs
import io.appwin.core.consent.ConsentPurpose
import io.appwin.core.consent.ConsentState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
  fun `configure demarre le pipeline une seule fois`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    assertFalse(AppwinCore.analytics.isPipelineStarted)
    AppwinCore.configure(context, projectAppId = "app-test")
    assertTrue(AppwinCore.analytics.isPipelineStarted)
    val first = AppwinCore.analytics.ensurePipeline(
      AnalyticsRuntime.Host(context, "app-test", null),
    )
    AppwinCore.configure(context, projectAppId = "app-test")
    assertTrue(AppwinCore.analytics.isPipelineStarted)
    assertSame(
      first,
      AppwinCore.analytics.ensurePipeline(AnalyticsRuntime.Host(context, "app-test", null)),
    )
  }

  @Test
  fun `un consentement pose avant configure s applique dans l ordre au configure`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    AppwinCore.setConsent(ConsentState.GRANTED)
    AppwinCore.setConsent(ConsentState.UNKNOWN, ConsentPurpose.ADVERTISING)
    AppwinCore.setConsent(ConsentState.DENIED, ConsentPurpose.MARKETING)
    // Readable before the store exists.
    assertEquals(ConsentState.UNKNOWN, AppwinCore.analytics.consentState(ConsentPurpose.ADVERTISING))

    AppwinCore.configure(context, projectAppId = "app-test")

    assertEquals(ConsentState.GRANTED, AppwinCore.analytics.consentState(ConsentPurpose.ANALYTICS))
    assertEquals(ConsentState.DENIED, AppwinCore.analytics.consentState(ConsentPurpose.MARKETING))
    assertEquals(ConsentState.UNKNOWN, AppwinCore.analytics.consentState(ConsentPurpose.ADVERTISING))
    val prefs = SharedPrefsAnalyticsPrefs(context)
    assertEquals("unknown", prefs.getString("appwin.analytics.app-test.consent.advertising"))
    assertEquals("denied", prefs.getString("appwin.analytics.app-test.consent.marketing"))
  }

  @Test
  fun `setConsent sans finalite regle les trois et se lit aussitot`() {
    AppwinCore.configure(ApplicationProvider.getApplicationContext(), projectAppId = "app-test")
    AppwinCore.setConsent(ConsentState.GRANTED)
    AppwinCore.setConsent(ConsentState.DENIED)
    ConsentPurpose.entries.forEach {
      assertEquals(ConsentState.DENIED, AppwinCore.analytics.consentState(it))
    }
  }

  @Test
  fun `le bus livre chaque event selon la finalite de l abonne`() {
    AppwinCore.configure(ApplicationProvider.getApplicationContext(), projectAppId = "app-test")
    val seen = mutableListOf<String>()
    val bus = AppwinCore.analytics.eventBus
    bus.subscribe(ConsentPurpose.ADVERTISING) { seen += "ads:${it.name}" }
    bus.subscribe(ConsentPurpose.ANALYTICS) { seen += "crash:${it.name}" }
    AppwinCore.setConsent(ConsentState.DENIED, ConsentPurpose.ADVERTISING)
    AppwinCore.track("purchase")
    assertEquals(listOf("crash:purchase"), seen)
    AppwinCore.setConsent(ConsentState.GRANTED, ConsentPurpose.ADVERTISING)
    AppwinCore.track("signup")
    assertEquals(listOf("crash:purchase", "ads:signup", "crash:signup"), seen)
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
  fun `un accord apres un lancement notifications coupees emet push_opt_in une fois`() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
      as android.app.NotificationManager
    org.robolectric.Shadows.shadowOf(manager).setNotificationsEnabled(false)
    val emitted = mutableListOf<String>()
    server.enqueue(
      MockResponse().setResponseCode(200).setBody("""{"token":"tok-1","customerSessionId":"sess-1"}"""),
    )
    configure()
    AppwinCore.pushOptInEmitter = { emitted += "push_opt_in" }
    AppwinCore.bootstrapSession()
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    AppwinCore.registerPushToken("fcm-token")
    assertTrue(emitted.isEmpty())

    org.robolectric.Shadows.shadowOf(manager).setNotificationsEnabled(true)
    AppwinCore.notePushAuthorizationGranted()
    AppwinCore.notePushAuthorizationGranted()

    assertEquals(listOf("push_opt_in"), emitted)
  }

  @Test
  fun `un appareil deja connu dont le drapeau est reclame n emet pas push_opt_in a l accord`() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    io.appwin.core.identity.SecureStore(context).set("appwin.core.deviceId", "device-from-0.15.0")
    val emitted = mutableListOf<String>()
    server.enqueue(
      MockResponse().setResponseCode(200).setBody("""{"token":"tok-1","customerSessionId":"sess-1"}"""),
    )
    configure()
    AppwinCore.pushOptInEmitter = { emitted += "push_opt_in" }
    AppwinCore.bootstrapSession()
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    AppwinCore.registerPushToken("fcm-token")
    assertTrue(emitted.isEmpty())

    AppwinCore.notePushAuthorizationGranted()

    assertTrue(emitted.isEmpty())
  }

  @Test
  fun `un accord avant tout jeton attend le jeton pour emettre`() = runTest {
    val emitted = mutableListOf<String>()
    server.enqueue(
      MockResponse().setResponseCode(200).setBody("""{"token":"tok-1","customerSessionId":"sess-1"}"""),
    )
    configure()
    AppwinCore.pushOptInEmitter = { emitted += "push_opt_in" }
    AppwinCore.notePushAuthorizationGranted()
    assertTrue(emitted.isEmpty())

    AppwinCore.bootstrapSession()
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    AppwinCore.registerPushToken("fcm-token")
    assertEquals(listOf("push_opt_in"), emitted)
  }

  @Test
  fun `registerPushToken waits for the session instead of posting without a bearer`() = runTest {
    // Only the session answer is queued: a token posted before the bearer
    // exists would consume it and fail. The registration must open (or join)
    // the session first, then post with its bearer.
    server.enqueue(session("tok-1"))
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    configure()

    AppwinCore.registerPushToken("fcm-token")

    val init = server.takeRequest()
    assertEquals("/api/sdk/v1/auth/init", init.path)
    val push = server.takeRequest()
    assertEquals("/api/sdk/support/v1/push-token", push.path)
    assertEquals("Bearer tok-1", push.getHeader("Authorization"))
    assertTrue(AppwinCore.hasRegisteredPushToken)
  }

  @Test
  fun `registerPushToken retries once with a fresh session after a 401`() = runTest {
    server.enqueue(session("tok-1"))
    configure()
    AppwinCore.bootstrapSession()

    // The bearer was rotated meanwhile (an identify on another path): the
    // first post is refused, a new session is minted, the second post lands.
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(session("tok-2"))
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    AppwinCore.registerPushToken("fcm-token")

    server.takeRequest() // initial auth/init
    assertEquals("/api/sdk/support/v1/push-token", server.takeRequest().path)
    assertEquals("/api/sdk/v1/auth/init", server.takeRequest().path)
    val retry = server.takeRequest()
    assertEquals("/api/sdk/support/v1/push-token", retry.path)
    assertEquals("Bearer tok-2", retry.getHeader("Authorization"))
    assertTrue(AppwinCore.hasRegisteredPushToken)
  }

  @Test
  fun `registerPushToken soft-fails on 5xx without crashing the host`() = runTest {
    server.enqueue(session("tok-1"))
    configure()
    AppwinCore.bootstrapSession()

    server.enqueue(MockResponse().setResponseCode(503))
    AppwinCore.registerPushToken("fcm-token")
    server.takeRequest()
    server.takeRequest()
    assertEquals(0, server.requestCount - 2)
    assertFalse(AppwinCore.hasRegisteredPushToken)
  }

  @Test
  fun `registerPushToken does not retry on client errors`() = runTest {
    server.enqueue(session("tok-1"))
    configure()
    AppwinCore.bootstrapSession()

    server.enqueue(MockResponse().setResponseCode(403))
    val error = runCatching { AppwinCore.registerPushToken("fcm-token") }.exceptionOrNull()
    assertTrue(error is AppwinApiException.Http)
    assertEquals(403, (error as AppwinApiException.Http).status)
    server.takeRequest()
    server.takeRequest()
    assertEquals(0, server.requestCount - 2)
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

  @Test
  fun `identify restores the push registration under the new session`() = runTest {
    server.enqueue(session("tok-anonymous"))
    configure()
    AppwinCore.bootstrapSession()
    server.takeRequest()
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    AppwinCore.registerPushToken("fcm-token")
    server.takeRequest()
    server.enqueue(session("tok-identified"))
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    AppwinCore.identify("next-user")
    assertEquals("/api/sdk/v1/auth/init", server.takeRequest().path)
    val registration = server.takeRequest()
    assertEquals("/api/sdk/support/v1/push-token", registration.path)
    assertEquals("Bearer tok-identified", registration.getHeader("Authorization"))
    assertTrue(registration.body.readUtf8().contains("fcm-token"))
  }

  private fun session(token: String) =
    MockResponse()
      .setResponseCode(200)
      .setBody("""{"token":"$token","customerSessionId":"sess-1"}""")
}
