package io.appwin.analytics.replay

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.flutter.embedding.android.FlutterSurfaceView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FlutterOnlyTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  /** A decor with a Flutter view holding its surface, laid out 400 x 800. */
  private fun decor(extra: (FrameLayout) -> Unit = {}): FrameLayout {
    val decor = FrameLayout(context)
    val flutterView = FrameLayout(context).apply { addView(FlutterSurfaceView(context), MATCH_PARENT, MATCH_PARENT) }
    decor.addView(flutterView, MATCH_PARENT, MATCH_PARENT)
    extra(flutterView)
    decor.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
    )
    decor.layout(0, 0, 400, 800)
    return decor
  }

  @Test
  fun `a Flutter surface alone is Flutter only`() {
    assertTrue(ScreenCapturer.isFlutterOnly(decor()))
  }

  @Test
  fun `a platform view or a native view on screen is not`() {
    assertFalse(ScreenCapturer.isFlutterOnly(decor { it.addView(TextView(context).apply { text = "native" }, 200, 100) }))
  }

  @Test
  fun `a hidden native view does not count`() {
    assertTrue(ScreenCapturer.isFlutterOnly(decor { it.addView(TextView(context).apply { visibility = View.GONE }, 200, 100) }))
  }

  @Test
  fun `a window without Flutter is not`() {
    val decor = FrameLayout(context).apply { addView(TextView(context), MATCH_PARENT, MATCH_PARENT) }
    decor.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
    )
    decor.layout(0, 0, 400, 800)
    assertFalse(ScreenCapturer.isFlutterOnly(decor))
  }
}
