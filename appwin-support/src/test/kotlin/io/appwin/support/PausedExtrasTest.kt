package io.appwin.support

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.appwin.core.availability.AppwinPause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PausedExtrasTest {
  private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun `the pause survives the intent, whatever the singletons hold after a process death`() {
    val pause = AppwinPause("2026-11-15T10:00:00.000Z", "On fait une pause", "Dites-lui de payer.")
    val intent = MessengerActivity.pausedIntent(context, pause)
    assertEquals(pause, pauseFromExtras(intent))
  }

  @Test
  fun `an empty title and message are kept as they are`() {
    val pause = AppwinPause("2026-11-15T10:00:00.000Z", "", "")
    assertEquals(pause, pauseFromExtras(MessengerActivity.pausedIntent(context, pause)))
  }

  @Test
  fun `an intent without the extras is not a pause`() {
    assertNull(pauseFromExtras(Intent(context, MessengerActivity::class.java)))
    assertNull(pauseFromExtras(Intent(context, MessengerActivity::class.java).putExtra("appwin.support.pause.title", "T")))
  }
}
