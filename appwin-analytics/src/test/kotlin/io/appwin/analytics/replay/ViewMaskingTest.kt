package io.appwin.analytics.replay

import android.graphics.Rect
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import io.appwin.analytics.AppwinReplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
class ViewMaskingTest {
  private val noMasking = ReplayConfig(maskAllText = false, maskAllImages = false)

  @Test
  fun `web views are masked with text and images shown`() {
    val rects = mask(noMasking) {}
    assertEquals(listOf(Rect(0, 0, 400, 100)), rects)
  }

  @Test
  fun `an unmasked web view is shown`() {
    val rects = mask(noMasking) { web -> AppwinReplay.unmask(web) }
    assertTrue(rects.isEmpty())
  }

  @Test
  fun `plain text follows the text setting`() {
    val rects = mask(ReplayConfig(maskAllImages = false)) { web -> AppwinReplay.unmask(web) }
    assertEquals(listOf(Rect(0, 100, 400, 150)), rects)
  }

  private fun mask(config: ReplayConfig, adjust: (WebView) -> Unit): List<Rect> {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val web = WebView(activity)
    val text = TextView(activity).apply { text = "hello" }
    root.addView(web, LinearLayout.LayoutParams(400, 100))
    root.addView(text, LinearLayout.LayoutParams(400, 50))
    adjust(web)
    val frame = FrameLayout(activity).apply { addView(root) }
    activity.setContentView(frame)
    frame.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
    )
    frame.layout(0, 0, 400, 800)
    // Robolectric's WebView provider swallows setFrame: the frame is set by hand.
    ReflectionHelpers.setField(web, "mRight", 400)
    ReflectionHelpers.setField(web, "mBottom", 100)
    val origin = IntArray(2).also(root::getLocationInWindow)
    val rects = ArrayList<Rect>()
    ViewMasking.collect(root, config, rects)
    return rects.map { Rect(it.left - origin[0], it.top - origin[1], it.right - origin[0], it.bottom - origin[1]) }
  }
}
