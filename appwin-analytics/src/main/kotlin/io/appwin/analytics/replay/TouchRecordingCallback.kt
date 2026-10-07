package io.appwin.analytics.replay

import android.graphics.PointF
import android.os.Build
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window
import kotlin.math.hypot

/**
 * Sees every touch of a window before the app does, without consuming any:
 * the original callback is chained untouched. Window coordinates, so they
 * line up with the captured frame.
 *
 * Only taps count: a pointer that lifts further than [tapSlopPx] from where
 * it went down was a scroll or a drag, as on iOS.
 */
internal class TouchRecordingCallback(
  val delegate: Window.Callback,
  private val tapSlopPx: Float,
  private val onTap: (x: Float, y: Float) -> Unit,
) : Window.Callback by delegate {
  private val starts = HashMap<Int, PointF>()

  override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
    if (event != null) runCatching { observe(event) }
    return delegate.dispatchTouchEvent(event)
  }

  private fun observe(event: MotionEvent) {
    val index = event.actionIndex
    val pointer = event.getPointerId(index)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        starts.clear()
        starts[pointer] = PointF(event.getX(index), event.getY(index))
      }
      MotionEvent.ACTION_POINTER_DOWN -> starts[pointer] = PointF(event.getX(index), event.getY(index))
      MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
        val start = starts.remove(pointer) ?: return
        val x = event.getX(index)
        val y = event.getY(index)
        if (hypot(x - start.x, y - start.y) <= tapSlopPx) onTap(x, y)
      }
      MotionEvent.ACTION_CANCEL -> starts.clear()
    }
  }

  // Java default methods are not covered by `by` delegation: forwarded by hand.
  override fun onProvideKeyboardShortcuts(
    data: MutableList<KeyboardShortcutGroup>?,
    menu: Menu?,
    deviceId: Int,
  ) {
    delegate.onProvideKeyboardShortcuts(data, menu, deviceId)
  }

  override fun onPointerCaptureChanged(hasCapture: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) delegate.onPointerCaptureChanged(hasCapture)
  }
}
