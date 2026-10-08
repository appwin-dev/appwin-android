package io.appwin.analytics.replay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File

/**
 * H.264 MP4 through the hardware encoder, from the JPEG frames of a segment.
 *
 * ByteBuffer (YUV) input rather than an input Surface: a Surface stamps each
 * frame with the time it was posted, and frames recovered after a crash are
 * posted all at once. Here the presentation time is the capture offset.
 */
internal object Mp4SegmentEncoder {
  enum class Result {
    ENCODED,

    /** No codec could be created or configured: this device cannot record. */
    UNAVAILABLE,

    /** This segment only (stall, muxer, unreadable frames): the next may encode. */
    FAILED,
  }

  private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
  private const val TIMEOUT_US = 10_000L
  private const val DEADLINE_MS = 30_000L

  /** Whether this device can encode at all; checked once before recording anything. */
  fun probe(width: Int = 192, height: Int = 416): Boolean {
    val codec = runCatching { MediaCodec.createEncoderByType(MIME) }.getOrNull() ?: return false
    return try {
      configure(codec, width, height)
      codec.start()
      codec.stop()
      true
    } catch (_: Throwable) {
      false
    } finally {
      runCatching { codec.release() }
    }
  }

  /**
   * A still screen is one frame the video holds, not ten encoded again: the
   * JPEG of an unchanged frame is byte for byte the same, as on iOS. The last
   * two stay: MediaMuxer gives the last sample the length of the gap before
   * it, so a lone last frame after a still run would stretch the file.
   */
  fun dropRepeats(frames: List<Pair<Long, File>>): List<Pair<Long, File>> {
    var previous: ByteArray? = null
    return frames.filterIndexed { index, (_, file) ->
      val bytes = runCatching { file.readBytes() }.getOrNull()
      val repeat = bytes != null && previous?.contentEquals(bytes) == true
      previous = bytes
      !repeat || index >= frames.lastIndex - 1
    }
  }

  /** Writes [out]; no file unless [Result.ENCODED]. */
  fun encode(allFrames: List<Pair<Long, File>>, width: Int, height: Int, out: File): Result {
    val frames = dropRepeats(allFrames)
    if (frames.isEmpty()) return Result.FAILED
    val codec = runCatching { MediaCodec.createEncoderByType(MIME) }.getOrNull()
      ?: return Result.UNAVAILABLE
    try {
      configure(codec, width, height)
      codec.start()
    } catch (_: Throwable) {
      runCatching { codec.release() }
      return Result.UNAVAILABLE
    }
    var muxer: MediaMuxer? = null
    var muxerStarted = false
    return try {
      val writer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      muxer = writer
      val pixels = IntArray(width * height)
      val info = MediaCodec.BufferInfo()
      val deadline = SystemClock.elapsedRealtime() + DEADLINE_MS
      var track = -1
      var next = 0
      var inputDone = false
      var samples = 0
      var lastPtsUs = 0L
      while (true) {
        check(SystemClock.elapsedRealtime() < deadline) { "encoder stalled" }
        if (!inputDone) {
          val index = codec.dequeueInputBuffer(TIMEOUT_US)
          if (index >= 0) {
            if (next < frames.size) {
              val (offsetMs, file) = frames[next++]
              // Read before getInputImage, which invalidates the buffer. The whole
              // buffer, not w*h*3/2: plane strides can pad the frame past that.
              val size = checkNotNull(codec.getInputBuffer(index)).capacity()
              val image = checkNotNull(codec.getInputImage(index)) { "no input image" }
              loadPixels(file, width, height, pixels)
              writeYuv(image, pixels, width, height)
              // Strictly increasing, or the muxer refuses the sample.
              lastPtsUs = maxOf(offsetMs * 1_000, if (next == 1) 0 else lastPtsUs + 1)
              codec.queueInputBuffer(index, 0, size, lastPtsUs, 0)
            } else {
              codec.queueInputBuffer(index, 0, 0, lastPtsUs + 1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            }
          }
        }
        val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        when {
          outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            track = writer.addTrack(codec.outputFormat)
            writer.start()
            muxerStarted = true
          }
          outIndex >= 0 -> {
            val buffer = checkNotNull(codec.getOutputBuffer(outIndex))
            val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (!config && info.size > 0 && muxerStarted) {
              buffer.position(info.offset)
              buffer.limit(info.offset + info.size)
              writer.writeSampleData(track, buffer, info)
              samples++
            }
            codec.releaseOutputBuffer(outIndex, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
          }
        }
      }
      check(samples > 0) { "no sample encoded" }
      writer.stop()
      Result.ENCODED
    } catch (_: Throwable) {
      out.delete()
      Result.FAILED
    } finally {
      runCatching { codec.stop() }
      runCatching { codec.release() }
      runCatching { muxer?.release() }
    }
  }

  /** Some encoders list High yet refuse it at configure: they get their default profile. */
  private fun configure(codec: MediaCodec, width: Int, height: Int) {
    try {
      codec.configure(format(codec, width, height, highProfile = true), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    } catch (_: Exception) {
      codec.reset()
      codec.configure(format(codec, width, height, highProfile = false), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }
  }

  private fun format(codec: MediaCodec, width: Int, height: Int, highProfile: Boolean): MediaFormat {
    val capabilities = codec.codecInfo.getCapabilitiesForType(MIME)
    check(capabilities.videoCapabilities.isSizeSupported(width, height)) { "size unsupported" }
    return MediaFormat.createVideoFormat(MIME, width, height).apply {
      setInteger(
        MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
      )
      setInteger(MediaFormat.KEY_BIT_RATE, ReplayLimits.VIDEO_BITRATE)
      setInteger(MediaFormat.KEY_FRAME_RATE, 1)
      // One keyframe per segment: each segment starts a fresh codec, so its
      // first frame is the sync frame the player seeks to.
      setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, (ReplayLimits.SEGMENT_MS / 1_000).toInt())
      // VBR, not CBR: a CBR encoder pads a still screen up to the full
      // bitrate, and a replay is mostly still screens. A screen change may
      // overshoot; SEGMENT_MAX_BYTES stays the hard bound.
      val vbr = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
      if (capabilities.encoderCapabilities?.isBitrateModeSupported(vbr) == true) {
        setInteger(MediaFormat.KEY_BITRATE_MODE, vbr)
      }
      // High rather than Baseline where offered: same bitrate, sharper text.
      val high = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
      if (highProfile && capabilities.profileLevels.any { it.profile == high }) {
        setInteger(MediaFormat.KEY_PROFILE, high)
      }
    }
  }

  private fun loadPixels(file: File, width: Int, height: Int, pixels: IntArray) {
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val decoded = runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull()
    if (decoded == null) {
      pixels.fill(MASK_GRAY)
      return
    }
    val bitmap = if (decoded.width == width && decoded.height == height) decoded
    else Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    bitmap.recycle()
  }

  /** BT.601 limited range, chroma sampled from the top-left pixel of each 2x2 block. */
  private fun writeYuv(image: Image, argb: IntArray, width: Int, height: Int) {
    val (yPlane, uPlane, vPlane) = image.planes
    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    for (row in 0 until height) {
      val chromaRow = row % 2 == 0
      for (col in 0 until width) {
        val color = argb[row * width + col]
        val r = (color shr 16) and 0xff
        val g = (color shr 8) and 0xff
        val b = color and 0xff
        val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        yBuffer.put(row * yPlane.rowStride + col * yPlane.pixelStride, y.toByte())
        if (chromaRow && col % 2 == 0) {
          val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
          val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
          uBuffer.put((row / 2) * uPlane.rowStride + (col / 2) * uPlane.pixelStride, u.toByte())
          vBuffer.put((row / 2) * vPlane.rowStride + (col / 2) * vPlane.pixelStride, v.toByte())
        }
      }
    }
  }

  private const val MASK_GRAY = 0xFF9E9E9E.toInt()
}
