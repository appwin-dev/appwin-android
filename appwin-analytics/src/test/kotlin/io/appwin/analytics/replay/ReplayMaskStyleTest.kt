package io.appwin.analytics.replay

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplayMaskStyleTest {
  @Test
  fun `block takes the tint of what it covers`() {
    val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(255, 128, 0))
    ReplayMaskStyle.paint(bitmap, listOf(RectF(0f, 0f, 40f, 20f)))
    assertEquals(Color.rgb(229, 115, 0), bitmap.getPixel(20, 10))
  }

  @Test
  fun `dark surfaces get a lighter block`() {
    assertEquals(Color.rgb(35, 35, 35), ReplayMaskStyle.blockColor(Color.rgb(0, 0, 0)))
  }

  @Test
  fun `block hides the text under it`() {
    val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    for (x in 10..11) for (y in 8..11) bitmap.setPixel(x, y, Color.BLACK)
    ReplayMaskStyle.paint(bitmap, listOf(RectF(0f, 0f, 40f, 20f)))
    assertEquals(bitmap.getPixel(30, 10), bitmap.getPixel(11, 10))
  }
}
