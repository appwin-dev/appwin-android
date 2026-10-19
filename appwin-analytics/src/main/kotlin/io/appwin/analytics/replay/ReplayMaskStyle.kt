package io.appwin.analytics.replay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * How a mask looks: a rounded block in the average color of what it covers,
 * nudged off it so it still reads as a mask. Blends into the screen like a
 * loading skeleton; one color per block gives nothing away. Same as iOS.
 */
internal object ReplayMaskStyle {
  /** In dp: scaled to the frame's pixels so a mask looks the same at any capture size. */
  private const val CORNER_RADIUS_DP = 6f
  private const val SAMPLES = 8
  private const val FALLBACK = 0xFF999999.toInt()

  /** All colors are read before the first block is painted: overlapping masks must not sample each other. */
  fun paint(bitmap: Bitmap, rects: List<RectF>, pixelsPerDp: Float = 1f) {
    val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
    val blocks = rects.mapNotNull { rect ->
      val clipped = RectF(rect)
      if (!clipped.intersect(bounds) || clipped.isEmpty) null
      else clipped to blockColor(averageColor(bitmap, clipped))
    }
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    for ((rect, color) in blocks) {
      val radius = min(CORNER_RADIUS_DP * pixelsPerDp, min(rect.width(), rect.height()) / 2)
      paint.color = color
      canvas.drawRoundRect(rect, radius, radius, paint)
    }
  }

  /** Darker on a light surface, lighter on a dark one. */
  fun blockColor(color: Int): Int {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    val light = 0.299 * r + 0.587 * g + 0.114 * b > 127.5
    val target = if (light) 0 else 255
    val amount = if (light) 0.1 else 0.14
    fun mix(c: Int) = (c + (target - c) * amount).toInt().coerceIn(0, 255)
    return Color.rgb(mix(r), mix(g), mix(b))
  }

  /** Up to 8 x 8 samples of [bitmap] under [rect]. */
  fun averageColor(bitmap: Bitmap, rect: RectF): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    for (iy in 0 until SAMPLES) {
      val y = (rect.top + (rect.height() - 1) * iy / (SAMPLES - 1)).toInt()
      for (ix in 0 until SAMPLES) {
        val x = (rect.left + (rect.width() - 1) * ix / (SAMPLES - 1)).toInt()
        if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) continue
        val pixel = bitmap.getPixel(x, y)
        r += Color.red(pixel)
        g += Color.green(pixel)
        b += Color.blue(pixel)
        n++
      }
    }
    if (n == 0) return FALLBACK
    return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
  }
}
