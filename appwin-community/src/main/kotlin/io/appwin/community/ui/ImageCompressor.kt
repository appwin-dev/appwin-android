package io.appwin.community.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a camera original before upload.
 *
 * A phone photo is several MB: uploading it made the picker hang, and every
 * reader of the feed then paid the same download. Post images are shown at
 * screen width, so 1600 px covers a 3x display; avatars sit at 40-112dp.
 */
internal object ImageCompressor {
  const val PostMaxSidePx = 1600
  const val AvatarMaxSidePx = 512
  private const val JpegQuality = 80

  suspend fun compress(bytes: ByteArray, maxSidePx: Int): Pair<ByteArray, String> =
    withContext(Dispatchers.Default) { compressBlocking(bytes, maxSidePx) }

  private fun compressBlocking(bytes: ByteArray, maxSidePx: Int): Pair<ByteArray, String> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val srcMax = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)

    var sample = 1
    while (srcMax / sample > maxSidePx * 2) sample *= 2

    val decoded = BitmapFactory.decodeByteArray(
      bytes,
      0,
      bytes.size,
      BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return bytes to "image/jpeg"

    val scale = (maxSidePx.toFloat() / max(decoded.width, decoded.height).coerceAtLeast(1))
      .coerceAtMost(1f)
    // BitmapFactory ignores EXIF: without this a portrait photo lands sideways,
    // and re-encoding drops the tag that would have fixed it on display.
    val matrix = Matrix().apply {
      postScale(scale, scale)
      postRotate(exifRotation(bytes))
    }
    val output = if (!matrix.isIdentity) {
      Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        .also { created -> if (created !== decoded) decoded.recycle() }
    } else {
      decoded
    }

    val out = ByteArrayOutputStream()
    output.compress(Bitmap.CompressFormat.JPEG, JpegQuality, out)
    output.recycle()
    return out.toByteArray() to "image/jpeg"
  }

  private fun exifRotation(bytes: ByteArray): Float =
    runCatching {
      when (
        ExifInterface(ByteArrayInputStream(bytes))
          .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
      ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
      }
    }.getOrDefault(0f)
}
