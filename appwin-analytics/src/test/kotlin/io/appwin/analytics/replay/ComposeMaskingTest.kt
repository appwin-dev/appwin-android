package io.appwin.analytics.replay

import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.appwin.analytics.appwinMask
import io.appwin.analytics.appwinUnmask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeMaskingTest {
  private val photo = BitmapPainter(ImageBitmap(10, 10))
  private val icon = ImageVector.Builder(
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).addPath(PathData { moveTo(0f, 0f); lineTo(24f, 24f) }, fill = SolidColor(Color.Black)).build()

  @Test
  fun `image without content description is masked`() {
    val rects = mask(ReplayConfig()) {
      Image(photo, contentDescription = null, Modifier.offset(10.dp, 20.dp).size(50.dp))
    }
    assertEquals(listOf(Rect(10, 20, 60, 70)), rects)
  }

  @Test
  fun `image with content description is masked once`() {
    val rects = mask(ReplayConfig()) {
      Image(photo, contentDescription = "avatar", Modifier.size(50.dp))
    }
    assertEquals(listOf(Rect(0, 0, 50, 50)), rects)
  }

  @Test
  fun `icons and color fills stay visible`() {
    val rects = mask(ReplayConfig()) {
      Column {
        Image(rememberVectorPainter(icon), contentDescription = null, Modifier.size(24.dp))
        Image(ColorPainter(Color.Red), contentDescription = null, Modifier.size(24.dp))
      }
    }
    assertTrue(rects.isEmpty())
  }

  @Test
  fun `images follow the image setting only`() {
    val rects = mask(ReplayConfig(maskAllImages = false)) {
      Image(photo, contentDescription = null, Modifier.size(50.dp))
    }
    assertTrue(rects.isEmpty())
  }

  @Test
  fun `unmask lifts the image rule for descendants`() {
    val rects = mask(ReplayConfig()) {
      Box(Modifier.appwinUnmask()) {
        Image(photo, contentDescription = null, Modifier.size(50.dp))
      }
    }
    assertTrue(rects.isEmpty())
  }

  @Test
  fun `text and inputs keep their rules`() {
    val rects = mask(ReplayConfig(maskAllText = false, maskAllImages = false)) {
      Column {
        BasicText("visible", Modifier.size(100.dp, 20.dp))
        BasicTextField("secret", {}, Modifier.size(100.dp, 20.dp).appwinUnmask())
        Box(Modifier.size(100.dp, 20.dp).appwinMask())
      }
    }
    assertEquals(listOf(Rect(0, 20, 100, 40), Rect(0, 40, 100, 60)), rects)
  }

  @Test
  fun `text is masked when the text setting is on`() {
    val rects = mask(ReplayConfig(maskAllImages = false)) {
      BasicText("hello", Modifier.size(100.dp, 20.dp))
    }
    assertEquals(listOf(Rect(0, 0, 100, 20)), rects)
  }

  @Test
  fun `an unreadable tree masks the whole view whatever the settings`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val rects = ArrayList<Rect>()
    val viewRect = Rect(0, 0, 400, 800)
    ComposeMasking.collect(
      View(activity),
      viewRect,
      inheritedUnmask = false,
      ReplayConfig(maskAllText = false, maskAllImages = false),
      rects,
    )
    assertEquals(listOf(viewRect), rects)
  }

  private fun mask(config: ReplayConfig, content: @Composable () -> Unit): List<Rect> {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    activity.setContent(content = content)
    val window = activity.window.decorView
    window.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
    )
    window.layout(0, 0, 400, 800)
    val root = findComposeRoot(window)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(1f, Density(activity).density)
    val origin = IntArray(2).also(root::getLocationInWindow)
    val rects = ArrayList<Rect>()
    val viewRect = Rect(origin[0], origin[1], origin[0] + root.width, origin[1] + root.height)
    ComposeMasking.collect(root, viewRect, inheritedUnmask = false, config, rects)
    return rects.map { Rect(it.left - origin[0], it.top - origin[1], it.right - origin[0], it.bottom - origin[1]) }
  }

  private fun findComposeRoot(view: View): View =
    if (view is ViewRootForTest) {
      view
    } else {
      (0 until (view as ViewGroup).childCount).firstNotNullOf {
        runCatching { findComposeRoot(view.getChildAt(it)) }.getOrNull()
      }
    }
}
