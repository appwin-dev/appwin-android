package io.appwin.community.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a camera original before avatar upload.
 *
 * Avatars are shown at 40-112dp: shipping a multi-MB HEIC/JPEG makes every
 * PostCard pay that download. Match the account onboarding crop (~512px).
 */
internal object AvatarCompressor {
  private const val MaxSidePx = 512
  private const val JpegQuality = 80

  fun compress(bytes: ByteArray): Pair<ByteArray, String> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val srcMax = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)

    var sample = 1
    while (srcMax / sample > MaxSidePx * 2) sample *= 2

    val decoded = BitmapFactory.decodeByteArray(
      bytes,
      0,
      bytes.size,
      BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return bytes to "image/jpeg"

    val scale = MaxSidePx.toFloat() / max(decoded.width, decoded.height).coerceAtLeast(1)
    val scaled = if (scale < 1f) {
      val w = (decoded.width * scale).roundToInt().coerceAtLeast(1)
      val h = (decoded.height * scale).roundToInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(decoded, w, h, true).also { created ->
        if (created !== decoded) decoded.recycle()
      }
    } else {
      decoded
    }

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JpegQuality, out)
    scaled.recycle()
    return out.toByteArray() to "image/jpeg"
  }
}
