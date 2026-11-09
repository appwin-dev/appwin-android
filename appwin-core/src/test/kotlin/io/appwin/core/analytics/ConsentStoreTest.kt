package io.appwin.core.analytics

import io.appwin.core.consent.ConsentPurpose
import io.appwin.core.consent.ConsentState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentStoreTest {
  private val prefix = "appwin.analytics.test."

  @Test
  fun `les trois finalites sont accordees par defaut`() {
    val store = ConsentStore(InMemoryAnalyticsPrefs(), prefix)
    assertEquals(ConsentPurpose.entries.toList(), store.grantedPurposes)
    assertFalse(store.allDenied)
    assertFalse(store.anyUnknown)
  }

  @Test
  fun `chaque finalite se persiste separement`() {
    val prefs = InMemoryAnalyticsPrefs()
    ConsentStore(prefs, prefix).set(ConsentState.DENIED, ConsentPurpose.MARKETING)
    assertEquals("denied", prefs.getString("${prefix}consent.marketing"))
    assertNull(prefs.getString("${prefix}consent.analytics"))
    val reloaded = ConsentStore(prefs, prefix)
    assertEquals(ConsentState.DENIED, reloaded.state(ConsentPurpose.MARKETING))
    assertEquals(ConsentState.GRANTED, reloaded.state(ConsentPurpose.ANALYTICS))
    assertEquals(listOf(ConsentPurpose.ANALYTICS, ConsentPurpose.ADVERTISING), reloaded.grantedPurposes)
  }

  @Test
  fun `sans finalite le consentement regle les trois`() {
    val store = ConsentStore(InMemoryAnalyticsPrefs(), prefix)
    store.set(ConsentState.DENIED, null)
    assertTrue(store.allDenied)
    store.set(ConsentState.UNKNOWN, null)
    assertTrue(store.anyUnknown)
    assertTrue(store.grantedPurposes.isEmpty())
  }

  @Test
  fun `l ancienne cle unique est copiee sur les trois finalites puis effacee`() {
    val prefs = InMemoryAnalyticsPrefs()
    prefs.putString("appwin.analytics.test.consent", "denied")
    val store = ConsentStore(prefs, "appwin.analytics.test.")
    assertEquals(ConsentState.DENIED, store.state(ConsentPurpose.MARKETING))
    assertEquals("denied", prefs.getString("appwin.analytics.test.consent.marketing"))
    assertNull(prefs.getString("appwin.analytics.test.consent"))
    assertTrue(store.allDenied)
  }

  @Test
  fun `une reponse par finalite deja presente n est jamais ecrasee par l ancienne cle`() {
    val prefs = InMemoryAnalyticsPrefs()
    prefs.putString("${prefix}consent", "denied")
    prefs.putString("${prefix}consent.analytics", "granted")
    val store = ConsentStore(prefs, prefix)
    assertEquals(ConsentState.GRANTED, store.state(ConsentPurpose.ANALYTICS))
    assertEquals(ConsentState.DENIED, store.state(ConsentPurpose.MARKETING))
    assertEquals(ConsentState.DENIED, store.state(ConsentPurpose.ADVERTISING))
    assertNull(prefs.getString("${prefix}consent"))
  }

  @Test
  fun `un unknown persiste est relu tel quel au relancement`() {
    val prefs = InMemoryAnalyticsPrefs()
    ConsentStore(prefs, prefix).set(ConsentState.UNKNOWN, null)
    val relaunched = ConsentStore(prefs, prefix)
    assertTrue(relaunched.anyUnknown)
    assertEquals(ConsentState.UNKNOWN, relaunched.state(ConsentPurpose.ANALYTICS))
  }

  @Test
  fun `deux instances sur les memes prefs voient la derniere ecriture`() {
    val prefs = InMemoryAnalyticsPrefs()
    val a = ConsentStore(prefs, prefix)
    val b = ConsentStore(prefs, prefix)
    a.set(ConsentState.GRANTED, null)
    b.set(ConsentState.DENIED, null)
    assertTrue(a.allDenied)
  }

  @Test
  fun `la lecture du crash handler ne bloque pas sur le verrou, elle rend la derniere valeur connue`() {
    val inRead = CountDownLatch(1)
    val release = CountDownLatch(1)
    var blocking = false
    val prefs = object : AnalyticsPrefs {
      private val inner = InMemoryAnalyticsPrefs()
      override fun getString(key: String): String? {
        if (blocking) {
          inRead.countDown()
          release.await(5, TimeUnit.SECONDS)
        }
        return inner.getString(key)
      }
      override fun putString(key: String, value: String?) = inner.putString(key, value)
    }
    val store = ConsentStore(prefs, prefix)
    store.set(ConsentState.DENIED, ConsentPurpose.ANALYTICS)
    blocking = true
    val holder = Thread { store.state(ConsentPurpose.ANALYTICS) }.apply { start() }
    assertTrue(inRead.await(5, TimeUnit.SECONDS))
    // The lock is held by `holder`: this must answer at once, not wait.
    assertEquals(ConsentState.DENIED, store.stateWithoutBlocking(ConsentPurpose.ANALYTICS))
    release.countDown()
    holder.join()
  }
}
