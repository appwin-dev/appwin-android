package io.appwin.support.ui

import androidx.test.core.app.ApplicationProvider
import io.appwin.core.availability.AppwinPause
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PausedCopyTest {
  private val strings get() = SupportStrings(ApplicationProvider.getApplicationContext())

  @Test
  fun `the served text wins, the SDK strings fill what is empty`() {
    val served = pausedCopy(AppwinPause("2026-11-15T10:00:00.000Z", "On fait une pause", "Dites-lui."), strings)
    assertEquals("On fait une pause" to "Dites-lui.", served)
    val fallback = pausedCopy(AppwinPause("2026-11-15T10:00:00.000Z", "", ""), strings)
    assertEquals(strings.pausedTitle to strings.pausedMessage, fallback)
  }
}
