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
    done: (Bitmap?) -> Unit,
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
      if (drawn) done(bitmap) else {
        bitmap.recycle()
        done(null)
      }
    }
  }

  /**
   * A window copy leaves a `SurfaceView` black: its content is a separate
   * compositor layer, under a hole the window punches. Flutter draws its whole
   * UI in one, so its surfaces are copied too and painted under the window
   * copy, which keeps any native view laid over them. Other surfaces (video,
   * camera, maps) stay black: nothing masks what they show.
   */
  @RequiresApi(Build.VERSION_CODES.O)
  private fun withSurfaces(window: Bitmap, decor: View, main: Handler, done: (Bitmap?) -> Unit) {
    val surfaces = ArrayList<SurfaceView>()
    findFlutterSurfaces(decor, surfaces)
    if (surfaces.isEmpty()) return done(window)
    val scaleX = window.width.toFloat() / decor.width
    val scaleY = window.height.toFloat() / decor.height
    val copies = arrayOfNulls<Bitmap>(surfaces.size)
    var pending = surfaces.size
    val finish = {
      val frame = runCatching {
        Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
      }.getOrNull()
      if (frame == null) {
        window.recycle()
        copies.forEach { it?.recycle() }
        done(null)
      } else {
        val canvas = Canvas(frame)
        val location = IntArray(2)
        val decorLocation = IntArray(2).also(decor::getLocationInWindow)
        surfaces.forEachIndexed { index, surface ->
          val copy = copies[index] ?: return@forEachIndexed
          surface.getLocationInWindow(location)
          val left = (location[0] - decorLocation[0]) * scaleX
          val top = (location[1] - decorLocation[1]) * scaleY
          canvas.drawBitmap(
            copy,
            null,
            RectF(left, top, left + surface.width * scaleX, top + surface.height * scaleY),
            null,
          )
          copy.recycle()
        }
        canvas.drawBitmap(window, 0f, 0f, null)
        window.recycle()
        done(frame)
      }
    }
    surfaces.forEachIndexed { index, surface ->
      val width = (surface.width * scaleX).roundToInt().coerceAtLeast(1)
      val height = (surface.height * scaleY).roundToInt().coerceAtLeast(1)
      val copy = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
      val onResult = { ok: Boolean ->
        if (ok) copies[index] = copy else copy?.recycle()
        if (--pending == 0) finish()
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
