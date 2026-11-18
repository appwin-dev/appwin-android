package io.appwin.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionManagerTest {
  private lateinit var prefs: InMemoryAnalyticsPrefs
  private var nowMs = 1_788_500_000_000L

  @Before
  fun setUp() {
    prefs = InMemoryAnalyticsPrefs()
    nowMs = 1_788_500_000_000L
  }

  private fun makeManager(
    appVersion: String? = "1.0.0",
    deviceKnownBeforeConfigure: Boolean = false,
  ): SessionManager =
    SessionManager(
      prefs = prefs, keyPrefix = "appwin.analytics.test.", appVersion = appVersion,
      timeoutMs = 30 * 60_000L, maxAgeMs = 24 * 3_600_000L,
      now = { nowMs },
      deviceKnownBeforeConfigure = deviceKnownBeforeConfigure,
    )

  private fun advance(seconds: Long) {
    nowMs += seconds * 1000
  }

  @Test
  fun `le premier touch emet app_install puis session_start`() {
    val manager = makeManager()
    val events = manager.touch()
    assertEquals(listOf("app_install", "session_start"), events.map { it.name })
    assertEquals(events[0].sessionId, events[1].sessionId)
    assertEquals(manager.sessionId, events[1].sessionId)
    assertNotNull(prefs.getString("appwin.analytics.test.session.id"))
  }

  @Test
  fun `un appareil connu sans drapeau emet app_update, pas app_install`() {
    val manager = makeManager(deviceKnownBeforeConfigure = true)
    assertEquals(listOf("app_update", "session_start"), manager.touch().map { it.name })
    assertEquals("1", prefs.getString("appwin.analytics.test.installTracked"))
  }

  @Test
  fun `l ancien drapeau referrer compte comme deja installe`() {
    prefs.putString("appwin.analytics.test.referrer.tracked", "1")
    val manager = makeManager()
    assertEquals(listOf("app_update", "session_start"), manager.touch().map { it.name })
    assertEquals("1", prefs.getString("appwin.analytics.test.installTracked"))
  }

  @Test
  fun `un drapeau referrer pose apres la creation ne rend pas l installation connue`() {
    val manager = makeManager()
    // The referrer collector of a fresh install can win the race to the first touch.
    prefs.putString("appwin.analytics.test.referrer.tracked", "1")
    assertEquals(listOf("app_install", "session_start"), manager.touch().map { it.name })
  }

  @Test
  fun `un drapeau installTracked deja pose n emet ni app_install ni app_update`() {
    prefs.putString("appwin.analytics.test.installTracked", "1")
    assertEquals(listOf("session_start"), makeManager(deviceKnownBeforeConfigure = true).touch().map { it.name })
  }

  @Test
  fun `une installation neuve est fraiche, un appareil connu ou un drapeau ne l est pas`() {
    val key = "appwin.analytics.test."
    assertEquals(true, SessionManager.isFreshInstall(prefs, key, deviceKnownBeforeConfigure = false))
    assertEquals(false, SessionManager.isFreshInstall(prefs, key, deviceKnownBeforeConfigure = true))
    prefs.putString(key + "referrer.tracked", "1")
    assertEquals(false, SessionManager.isFreshInstall(prefs, key, deviceKnownBeforeConfigure = false))
    prefs.putString(key + "referrer.tracked", null)
    prefs.putString(key + "installTracked", "1")
    assertEquals(false, SessionManager.isFreshInstall(prefs, key, deviceKnownBeforeConfigure = false))
  }

  @Test
  fun `un touch dans la fenetre garde la session`() {
    val manager = makeManager()
    val started = manager.touch()
    advance(60)
    assertEquals(emptyList<SessionManager.StandardEvent>(), manager.touch())
    assertEquals(started.last().sessionId, manager.sessionId)
  }

  @Test
  fun `un touch apres timeout termine retroactivement et rouvre une session`() {
    val manager = makeManager()
    val first = manager.touch()
    advance(5)
    manager.touch()
    val lastActive = nowMs
    advance(31 * 60)
    val events = manager.touch()
    assertEquals(listOf("session_end", "session_start"), events.map { it.name })
    assertEquals(first.last().sessionId, events[0].sessionId)
    assertEquals(lastActive, events[0].occurredAtMs)
    assertEquals(5_000L, events[0].durationMs)
    assertNotEquals(first.last().sessionId, events[1].sessionId)
  }

  @Test
  fun `un relaunch dans la fenetre reprend la session`() {
    val started = makeManager().touch()
    advance(60)
    val relaunched = makeManager()
    assertEquals(emptyList<SessionManager.StandardEvent>(), relaunched.touch())
    assertEquals(started.last().sessionId, relaunched.sessionId)
  }

  @Test
  fun `un relaunch apres timeout termine depuis l'etat persiste sans re-emettre app_install`() {
    val first = makeManager().touch()
    val startMs = nowMs
    advance(45 * 60)
    val events = makeManager().touch()
    assertEquals(listOf("session_end", "session_start"), events.map { it.name })
    assertEquals(first.last().sessionId, events[0].sessionId)
    assertEquals(startMs, events[0].occurredAtMs)
    assertEquals(0L, events[0].durationMs)
  }

  @Test
  fun `une session trop vieille est recyclee meme active`() {
    val manager = makeManager()
    val first = manager.touch()
    repeat(146) {
      advance(10 * 60)
      manager.touch()
    }
    assertNotEquals(first.last().sessionId, manager.sessionId)
  }

  @Test
  fun `app_update est emis une seule fois au changement de version`() {
    makeManager().touch()
    advance(45 * 60)
    val events = makeManager(appVersion = "2.0.0").touch()
    assertEquals(listOf("session_end", "app_update", "session_start"), events.map { it.name })
    advance(45 * 60)
    val again = makeManager(appVersion = "2.0.0").touch()
    assertEquals(listOf("session_end", "session_start"), again.map { it.name })
  }

  @Test
  fun `reset oublie la session mais pas le flag d'install`() {
    val manager = makeManager()
    manager.touch()
    manager.reset()
    assertNull(manager.sessionId)
    assertEquals(listOf("session_start"), manager.touch().map { it.name })
  }
}
