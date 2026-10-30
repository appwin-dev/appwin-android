package io.appwin.core.push

import io.appwin.core.analytics.InMemoryAnalyticsPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushOptInFlagTest {
  private val keyPrefix = "appwin.analytics.test."

  @Test
  fun `push_opt_in se reclame une fois par installation`() {
    val prefs = InMemoryAnalyticsPrefs()
    assertTrue(PushOptInFlag.claim(prefs, keyPrefix, freshInstall = true, grantedThisProcess = false))
    assertFalse(PushOptInFlag.claim(prefs, keyPrefix, freshInstall = true, grantedThisProcess = false))
  }

  @Test
  fun `une mise a jour avec des utilisateurs deja inscrits pose le drapeau sans emettre`() {
    val prefs = InMemoryAnalyticsPrefs()
    assertFalse(PushOptInFlag.claim(prefs, keyPrefix, freshInstall = false, grantedThisProcess = false))
    assertEquals("1", prefs.getString(keyPrefix + "pushOptIn.tracked"))
    // Silent for good: a later grant seen in the process no longer counts.
    assertFalse(PushOptInFlag.claim(prefs, keyPrefix, freshInstall = false, grantedThisProcess = true))
  }

  @Test
  fun `un accord vu dans le processus emet meme sur un appareil connu`() {
    val prefs = InMemoryAnalyticsPrefs()
    assertTrue(PushOptInFlag.claim(prefs, keyPrefix, freshInstall = false, grantedThisProcess = true))
  }

  @Test
  fun `la decision pure`() {
    assertTrue(PushOptInFlag.shouldEmit(freshInstall = true, grantedThisProcess = false, alreadyClaimed = false))
    assertTrue(PushOptInFlag.shouldEmit(freshInstall = false, grantedThisProcess = true, alreadyClaimed = false))
    assertFalse(PushOptInFlag.shouldEmit(freshInstall = false, grantedThisProcess = false, alreadyClaimed = false))
    assertFalse(PushOptInFlag.shouldEmit(freshInstall = true, grantedThisProcess = true, alreadyClaimed = true))
  }

  @Test
  fun `sans pipeline le drapeau n est pas reclame et l event n est pas perdu`() {
    val prefs = InMemoryAnalyticsPrefs()
    var emitted = 0
    PushOptInFlag.report(prefs, keyPrefix, pipelineReady = false, freshInstall = true, grantedThisProcess = false) { emitted += 1 }
    assertEquals(0, emitted)
    assertEquals(null, prefs.getString(keyPrefix + "pushOptIn.tracked"))

    PushOptInFlag.report(prefs, keyPrefix, pipelineReady = true, freshInstall = true, grantedThisProcess = false) { emitted += 1 }
    PushOptInFlag.report(prefs, keyPrefix, pipelineReady = true, freshInstall = true, grantedThisProcess = false) { emitted += 1 }
    assertEquals(1, emitted)
  }
}
