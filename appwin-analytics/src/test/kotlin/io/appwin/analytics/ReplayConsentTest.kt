@file:OptIn(AppwinInternalApi::class)

package io.appwin.analytics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.appwin.analytics.replay.ReplayController
import io.appwin.analytics.replay.ReplayStorage
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.consent.ConsentPurpose
import io.appwin.core.consent.ConsentState
import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReplayConsentTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun recordSomething(storage: ReplayStorage) {
    val video = File(context.cacheDir, "segment.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    assertTrue(storage.queue.enqueue(video, buildJsonObject { put("seq", 0) }))
    File(storage.encodeDir.parentFile, "frames/open-segment").apply { mkdirs() }
      .resolve("frame-0.jpg").writeBytes(byteArrayOf(1))
  }

  private fun waitUntil(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
  }

  // AppwinCore is process-wide and not resettable from this module: one scenario.
  @Test
  fun `refuser marketing ne touche pas la video, refuser analytics par Core la purge`() {
    AppwinCore.configure(context, "app-replay-consent")
    val storage = ReplayStorage(context, "app-replay-consent")
    recordSomething(storage)
    ReplayController.listenToConsent()

    AppwinCore.setConsent(ConsentState.DENIED, ConsentPurpose.MARKETING)
    Thread.sleep(300)
    assertEquals(1, storage.queue.pending().size)

    AppwinCore.setConsent(ConsentState.DENIED, ConsentPurpose.ANALYTICS)
    waitUntil { storage.queue.pending().isEmpty() }
    assertEquals(0, storage.queue.pending().size)
    assertTrue(!File(storage.encodeDir.parentFile, "frames/open-segment").exists())
  }
}
