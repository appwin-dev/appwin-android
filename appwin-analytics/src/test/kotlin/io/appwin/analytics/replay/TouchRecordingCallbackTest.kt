package io.appwin.analytics.replay

import android.view.MotionEvent
import android.view.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TouchRecordingCallbackTest {
  private val taps = mutableListOf<Pair<Float, Float>>()
  private var delivered = 0

  private val delegate = Proxy.newProxyInstance(
    Window.Callback::class.java.classLoader,
    arrayOf(Window.Callback::class.java),
  ) { _, method, _ ->
    if (method.name == "dispatchTouchEvent") {
      delivered++
      true
    } else if (method.returnType == Boolean::class.javaPrimitiveType) {
      false
    } else {
      null
    }
  } as Window.Callback

  private val callback = TouchRecordingCallback(delegate, tapSlopPx = 20f) { x, y -> taps.add(x to y) }

  private fun send(action: Int, x: Float, y: Float) {
    val event = MotionEvent.obtain(0, 0, action, x, y, 0)
    callback.dispatchTouchEvent(event)
    event.recycle()
  }

  @Test
  fun `a short press is a tap where the finger lifts`() {
    send(MotionEvent.ACTION_DOWN, 100f, 100f)
    send(MotionEvent.ACTION_UP, 110f, 105f)
    assertEquals(listOf(110f to 105f), taps)
    assertEquals(2, delivered)
  }

  @Test
  fun `a scroll is not a tap`() {
    send(MotionEvent.ACTION_DOWN, 100f, 100f)
    send(MotionEvent.ACTION_MOVE, 100f, 200f)
    send(MotionEvent.ACTION_UP, 100f, 300f)
    assertTrue(taps.isEmpty())
    assertEquals(3, delivered)
  }

  @Test
  fun `a cancelled gesture is not a tap`() {
    send(MotionEvent.ACTION_DOWN, 100f, 100f)
    send(MotionEvent.ACTION_CANCEL, 100f, 100f)
    send(MotionEvent.ACTION_UP, 100f, 100f)
    assertTrue(taps.isEmpty())
  }
}
