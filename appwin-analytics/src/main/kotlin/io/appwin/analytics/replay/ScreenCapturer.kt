package io.appwin.analytics.replay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

internal object ScreenCapturer {
  /**
   * Encoded size for a window of [widthPx] x [heightPx]: two pixels per dp, like
   * iOS per point, never past the screen's own pixels. Multiples of 16, which every
   * H.264 encoder accepts.
   */
  fun frameSize(widthPx: Int, heightPx: Int, density: Float): Pair<Int, Int> {
    val scale = (ReplayLimits.CAPTURE_SCALE / density.coerceAtLeast(1f)).coerceAtMost(1f)
    return align(widthPx * scale) to align(heightPx * scale)
  }

  /**
   * Calls [done] on [main] with a [width] x [height] copy of the window, or
   * null. PixelCopy reads what the compositor shows (API 26+); below, the
   * decor view is redrawn into a software canvas.
   */
  fun capture(
    window: Window,
    decor: View,
    width: Int,
    height: Int,
    main: Handler,
    done: (CapturedFrame?) -> Unit,
  ) {
    val bitmap = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
      .getOrNull()
      ?: return done(null)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        PixelCopy.request(
          window,
          bitmap,
          { result ->
            if (result == PixelCopy.SUCCESS) {
              withSurfaces(bitmap, decor, main, done)
            } else {
              bitmap.recycle()
              done(null)
            }
          },
          main,
        )
      } catch (_: IllegalArgumentException) {
        // No surface yet (window being attached or torn down).
        bitmap.recycle()
        done(null)
      }
    } else {
      val drawn = runCatching {
        val canvas = Canvas(bitmap)
        canvas.scale(width.toFloat() / decor.width, height.toFloat() / decor.height)
        decor.draw(canvas)
      }.isSuccess
      if (drawn) done(CapturedFrame(bitmap, emptyList())) else {
        bitmap.recycle()
        done(null)
      }
    }
  }

  /**
   * A window copy leaves a `SurfaceView` black: its content is a separate
   * compositor layer, under a hole the window punches. Flutter draws its whole
   * UI in one, so its surfaces are copied too, to be painted under the window
   * copy, which keeps any native view laid over them. Other surfaces (video,
   * camera, maps) stay black: nothing masks what they show.
   */
  @RequiresApi(Build.VERSION_CODES.O)
  private fun withSurfaces(window: Bitmap, decor: View, main: Handler, done: (CapturedFrame?) -> Unit) {
    val surfaces = ArrayList<SurfaceView>()
    findFlutterSurfaces(decor, surfaces)
    if (surfaces.isEmpty()) return done(CapturedFrame(window, emptyList()))
    val scaleX = window.width.toFloat() / decor.width
    val scaleY = window.height.toFloat() / decor.height
    val decorLocation = IntArray(2).also(decor::getLocationInWindow)
    val location = IntArray(2)
    val copies = arrayOfNulls<Pair<Bitmap, RectF>>(surfaces.size)
    var pending = surfaces.size
    surfaces.forEachIndexed { index, surface ->
      surface.getLocationInWindow(location)
      val left = (location[0] - decorLocation[0]) * scaleX
      val top = (location[1] - decorLocation[1]) * scaleY
      val rect = RectF(left, top, left + surface.width * scaleX, top + surface.height * scaleY)
      val width = (surface.width * scaleX).roundToInt().coerceAtLeast(1)
      val height = (surface.height * scaleY).roundToInt().coerceAtLeast(1)
      val copy = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
      val onResult = { ok: Boolean ->
        if (ok && copy != null) copies[index] = copy to rect else copy?.recycle()
        if (--pending == 0) done(CapturedFrame(window, copies.filterNotNull()))
      }
      if (copy == null || !surface.holder.surface.isValid) {
        onResult(false)
      } else {
        try {
          PixelCopy.request(surface, copy, { result -> onResult(result == PixelCopy.SUCCESS) }, main)
        } catch (_: IllegalArgumentException) {
          onResult(false)
        }
      }
    }
  }

  /**
   * Whether Flutter's surface is all [decor] shows: a visible `FlutterSurfaceView`
   * and no other view that draws, the window's own bar backgrounds aside. Copying
   * that surface costs the app's threads under 1 ms (Galaxy S22), so such a frame
   * need not wait for the screen to settle. A platform view or a native screen
   * over Flutter brings the usual wait back.
   */
  fun isFlutterOnly(decor: View): Boolean {
    var surfaces = 0
    fun walk(view: View): Boolean {
      if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0 || view.alpha <= 0f) {
        return true
      }
      if (view is SurfaceView && view.javaClass.name == FLUTTER_SURFACE_VIEW) {
        surfaces++
        return true
      }
      if (view.id == android.R.id.statusBarBackground || view.id == android.R.id.navigationBarBackground) {
        return true
      }
      if (view !is ViewGroup) return false
      for (index in 0 until view.childCount) {
        if (!walk(view.getChildAt(index))) return false
      }
      return true
    }
    return walk(decor) && surfaces > 0
  }

  private fun findFlutterSurfaces(view: View, out: MutableList<SurfaceView>) {
    if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return
    if (view is SurfaceView && view.javaClass.name == FLUTTER_SURFACE_VIEW) {
      out.add(view)
      return
    }
    if (view is ViewGroup) {
      for (index in 0 until view.childCount) findFlutterSurfaces(view.getChildAt(index), out)
    }
  }

  private const val FLUTTER_SURFACE_VIEW = "io.flutter.embedding.android.FlutterSurfaceView"

  private fun align(value: Float): Int = ((value / 16f).roundToInt() * 16).coerceIn(64, 4096)
}

/**
 * The copies of one capture. Put together by [render] on the worker thread:
 * drawing full-screen bitmaps in a software canvas costs the main thread
 * several milliseconds, enough to drop a frame of a scroll.
 */
internal class CapturedFrame(
  private val window: Bitmap,
  /** Flutter surfaces, each with where it lies in [window]. */
  private val surfaces: List<Pair<Bitmap, RectF>>,
) {
  /** The frame, or null when no bitmap could be allocated. Consumes the copies. */
  fun render(): Bitmap? {
    if (surfaces.isEmpty()) return window
    val frame = runCatching {
      Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
    }.getOrNull()
    if (frame != null) {
      val canvas = Canvas(frame)
      for ((copy, rect) in surfaces) canvas.drawBitmap(copy, null, rect, null)
      canvas.drawBitmap(window, 0f, 0f, null)
    }
    recycle()
    return frame
  }

  fun recycle() {
    window.recycle()
    surfaces.forEach { it.first.recycle() }
  }
}
