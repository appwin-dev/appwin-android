@file:OptIn(AppwinInternalApi::class)

package io.appwin.analytics.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.Window
import io.appwin.core.AppwinInternalApi
import io.appwin.core.analytics.AnalyticsConsent
import io.appwin.core.analytics.AppwinAnalyticsHost
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Records the analytics session as video (ADR-0057): one masked frame per
 * second while the app is in the foreground, cut into ~10 s segments that
 * are encoded and queued for upload.
 *
 * Threads: the view hierarchy is read and the window copy requested on the
 * main thread; composing the copies, masking paint, disk writes and encoding
 * run on one worker thread, which alone owns the open segment.
 */
internal class ReplayRecorder(
  private val context: Context,
  storage: ReplayStorage,
  val config: ReplayConfig,
  private val runtime: () -> String,
  private val bridgedMasks: () -> List<Rect>,
  private val scope: CoroutineScope,
) {
  private class OpenSegment(val info: SegmentInfo, val dir: File) {
    var frames = 0
    var touches = 0
    var screens = 0
  }

  private val main = Handler(Looper.getMainLooper())
  private val workerThread = HandlerThread("appwin-replay").apply { start() }
  private val worker = Handler(workerThread.looper)
  private val segments = storage.segments
  private val queue = storage.queue
  private val encodeDir = storage.encodeDir
  private val budget = ReplaySessionBudget(storage.prefs)

  val uploader = ReplayUploader(
    queue = queue,
    sender = ApiSegmentSender,
    consent = { AppwinAnalyticsHost.consent },
    onForbidden = { main.post { stop(purge = true) } },
    scope = scope,
    retryDelayMs = AppwinAnalyticsHost::retryDelayMs,
    reauthorize = AppwinAnalyticsHost::reauthorize,
  )

  @Volatile
  var isRunning = false
    private set

  /** Main thread. When the pending PixelCopy was requested, 0 when none is. */
  private var frameRequestedAt = 0L

  /** Main thread. `uptimeMillis` of the last frame requested, 0 before any. */
  private var lastFrameAt = 0L
  private var sampling: Pair<String, Boolean>? = null
  private var foregroundJob: Job? = null
  private val wrapped = java.util.WeakHashMap<Window, TouchRecordingCallback>()
  private val draws = java.util.WeakHashMap<View, WindowDraws>()

  /** Worker thread. */
  private var open: OpenSegment? = null

  /** Set by the worker once the session has used its hour, read on main to stop capturing. */
  @Volatile
  private var exhaustedSession: String? = null

  @Volatile
  private var lastScreen: String? = null

  private val tick = object : Runnable {
    override fun run() {
      if (!isRunning) return
      main.postDelayed(this, ReplayPacer.POLL_MS)
      runCatching { captureFrame() }.onFailure { Log.w(TAG, "replay: frame skipped", it) }
    }
  }

  /** Main thread. Encodes what a previous run left behind, then records. */
  fun start() {
    if (isRunning || encoderUnavailable) return
    isRunning = true
    worker.post {
      if (!Mp4SegmentEncoder.probe()) {
        disableEncoder()
        main.post { stop(purge = false) }
        return@post
      }
      encodeDir.deleteRecursively()
      if (AppwinAnalyticsHost.consent == AnalyticsConsent.DENIED) {
        segments.purge()
        queue.purge()
        return@post
      }
      segments.leftovers().forEach(::encodeAndQueue)
      uploader.flush()
    }
    foregroundJob = scope.launch {
      AppwinAnalyticsHost.inForeground.collect { foreground ->
        if (!foreground) worker.post { closeSegment() }
      }
    }
    main.post(tick)
  }

  /** Main thread. [purge] drops everything recorded and queued (403, DENIED). */
  fun stop(purge: Boolean) {
    isRunning = false
    main.removeCallbacks(tick)
    foregroundJob?.cancel()
    for ((window, callback) in wrapped) {
      if (window.callback === callback) window.callback = callback.delegate
    }
    wrapped.clear()
    for ((decor, listener) in draws) {
      if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnDrawListener(listener)
    }
    draws.clear()
    if (purge) uploader.cancel()
    worker.post {
      if (purge) {
        open = null
        segments.purge()
        queue.purge()
      } else {
        closeSegment()
      }
    }
  }

  /** Any thread. */
  fun onScreen(name: String) {
    lastScreen = name
    val at = System.currentTimeMillis()
    worker.post {
      val segment = open ?: return@post
      if (segment.screens >= ReplayLimits.MAX_SCREENS) return@post
      segment.screens++
      segments.appendScreen(segment.dir, ReplayScreen(at - segment.info.startedAtMs, name))
    }
  }

  /** Any thread. DENIED wipes what was recorded; GRANTED sends the backlog. */
  fun onConsentChanged(consent: AnalyticsConsent) {
    when (consent) {
      AnalyticsConsent.DENIED -> {
        uploader.cancel()
        worker.post {
          open = null
          segments.purge()
          queue.purge()
        }
      }
      AnalyticsConsent.GRANTED -> uploader.flush(resetBackoff = true)
      AnalyticsConsent.UNKNOWN -> worker.post { closeSegment() }
    }
  }

  private fun captureFrame() {
    if (encoderUnavailable) return
    val now = System.currentTimeMillis()
    // A copy that never called back must not stall recording for good.
    if (frameRequestedAt != 0L && now - frameRequestedAt < STALE_REQUEST_MS) return
    if (AppwinAnalyticsHost.consent != AnalyticsConsent.GRANTED) return
    if (!AppwinAnalyticsHost.inForeground.value) return
    val sessionId = AppwinAnalyticsHost.sessionId ?: return
    if (sessionId == exhaustedSession) return
    if (!isSampled(sessionId)) {
      worker.post { closeSegment() }
      return
    }
    val activity = AppwinAnalyticsHost.resumedActivity ?: return
    val window = activity.window ?: return
    val decor = window.peekDecorView() ?: return
    if (decor.width <= 0 || decor.height <= 0) return
    recordTouches(window)
    val drawn = draws.getOrPut(decor) {
      WindowDraws().also { decor.viewTreeObserver.addOnDrawListener(it) }
    }
    val uptime = SystemClock.uptimeMillis()
    val touches = wrapped[window]
    // A Flutter screen alone costs nothing to copy: it is recorded while
    // scrolled or animated, as on iOS. Flutter draws in its surface, which
    // the window's draws do not see anyway.
    val due = if (ScreenCapturer.isFlutterOnly(decor)) {
      ReplayPacer.isDue(uptime, lastFrameAt, touching = false, lastTouchAt = 0L)
    } else {
      ReplayPacer.isDue(
        uptime,
        lastFrameAt,
        touching = touches?.touching == true,
        lastTouchAt = touches?.lastTouchAt ?: 0L,
        lastDrawAt = drawn.lastDrawAt,
      )
    }
    if (!due) return
    lastFrameAt = uptime

    val (width, height) =
      ScreenCapturer.frameSize(decor.width, decor.height, context.resources.displayMetrics.density)
    val before = masks(decor)
    val at = now
    val scaleX = width.toFloat() / decor.width
    val scaleY = height.toFloat() / decor.height
    frameRequestedAt = at
    ScreenCapturer.capture(window, decor, width, height, main) { captured ->
      frameRequestedAt = 0L
      if (captured == null) return@capture
      // PixelCopy lands a frame or so later: the union of both readings covers
      // a view that moved in between.
      val after = runCatching { masks(decor) }.getOrNull()
      if (!isRunning || after == null) {
        captured.recycle()
        return@capture
      }
      worker.post {
        val bitmap = captured.render() ?: return@post
        onFrame(sessionId, at, bitmap, before + after, scaleX, scaleY)
      }
    }
  }

  private fun masks(decor: View): List<Rect> {
    val rects = ArrayList<Rect>()
    ViewMasking.collect(decor, config, rects)
    rects.addAll(bridgedMasks())
    return rects
  }

  private fun isSampled(sessionId: String): Boolean {
    sampling?.takeIf { it.first == sessionId }?.let { return it.second }
    return ReplaySampling.isSampled(sessionId, config.sampleRate).also { sampling = sessionId to it }
  }

  private fun recordTouches(window: Window) {
    val current = window.callback ?: return
    // Another library may have wrapped ours since: wrapping again would count every tap twice.
    if (current is TouchRecordingCallback || wrapped.containsKey(window)) return
    val slop = TAP_SLOP_DP * context.resources.displayMetrics.density
    val callback = TouchRecordingCallback(current, slop) { x, y ->
      val decor = window.peekDecorView() ?: return@TouchRecordingCallback
      if (decor.width <= 0 || decor.height <= 0) return@TouchRecordingCallback
      onTap(x / decor.width, y / decor.height, System.currentTimeMillis())
    }
    window.callback = callback
    wrapped[window] = callback
  }

  private fun onTap(x: Float, y: Float, at: Long) {
    if (!isRunning) return
    worker.post {
      val segment = open ?: return@post
      if (segment.touches >= ReplayLimits.MAX_TOUCHES) return@post
      segment.touches++
      val touch = ReplayTouch(at - segment.info.startedAtMs, x.toDouble(), y.toDouble())
      segments.appendTouch(segment.dir, touch)
    }
  }

  private fun onFrame(
    sessionId: String,
    at: Long,
    bitmap: Bitmap,
    masks: List<Rect>,
    scaleX: Float,
    scaleY: Float,
  ) {
    try {
      if (budget.isExhausted(sessionId)) {
        exhaustedSession = sessionId
        closeSegment()
        return
      }
      ReplayMaskStyle.paint(
        bitmap,
        masks.map {
          RectF(
            floor(it.left * scaleX),
            floor(it.top * scaleY),
            ceil(it.right * scaleX),
            ceil(it.bottom * scaleY),
          )
        },
        pixelsPerDp = scaleX * context.resources.displayMetrics.density,
      )
      val segment = segmentFor(sessionId, at, bitmap.width, bitmap.height) ?: return
      val offset = at - segment.info.startedAtMs
      if (!segments.writeFrame(segment.dir, offset, bitmap)) return
      segment.frames++
      budget.addFrame(sessionId)
      if (segment.frames >= FRAMES_PER_SEGMENT) closeSegment()
    } finally {
      bitmap.recycle()
    }
  }

  private fun segmentFor(sessionId: String, at: Long, width: Int, height: Int): OpenSegment? {
    open?.let { current ->
      val info = current.info
      val rotate = info.sessionId != sessionId || info.width != width || info.height != height ||
        at - info.startedAtMs >= ReplayLimits.SEGMENT_MS
      if (!rotate) return current
      closeSegment()
    }
    val info = SegmentInfo(sessionId, budget.nextSeq(sessionId), at, width, height, runtime())
    val dir = segments.open(info) ?: return null
    val segment = OpenSegment(info, dir)
    open = segment
    // The first segment of a session names the screen it starts on, which a
    // screen() call made before recording began would otherwise leave unknown.
    val screen = lastScreen
    if (info.seq == 0 && screen != null) {
      segment.screens++
      segments.appendScreen(dir, ReplayScreen(0, screen))
    }
    return segment
  }

  private fun closeSegment() {
    val segment = open ?: return
    open = null
    encodeAndQueue(segment.dir)
  }

  private fun encodeAndQueue(dir: File) {
    val recorded = segments.read(dir)
    if (recorded == null || recorded.frames.isEmpty() || encoderUnavailable) {
      segments.delete(dir)
      return
    }
    val info = recorded.info
    encodeDir.mkdirs()
    val out = File(encodeDir, "${info.sessionId}_${info.seq}.mp4")
    when (Mp4SegmentEncoder.encode(recorded.frames, info.width, info.height, out)) {
      Mp4SegmentEncoder.Result.ENCODED -> Unit
      Mp4SegmentEncoder.Result.UNAVAILABLE -> {
        disableEncoder()
        segments.purge()
        main.post { stop(purge = false) }
        return
      }
      Mp4SegmentEncoder.Result.FAILED -> {
        Log.w(TAG, "replay: segment dropped, encoding failed")
        segments.delete(dir)
        return
      }
    }
    val endedAt = info.startedAtMs + recorded.frames.last().first + ReplayLimits.FRAME_INTERVAL_MS
    queue.enqueue(out, ReplayMeta.build(info, endedAt, recorded.screens, recorded.touches))
    segments.delete(dir)
    uploader.flush()
  }

  private companion object {
    const val TAG = "Appwin"
    const val STALE_REQUEST_MS = 5_000L

    /** The 20 pt of `ReplayTouchRecognizer` on iOS. */
    const val TAP_SLOP_DP = 20f
    val FRAMES_PER_SEGMENT = (ReplayLimits.SEGMENT_MS / ReplayLimits.FRAME_INTERVAL_MS).toInt()

    /**
     * Per process: a device that cannot create or configure the encoder is
     * not retried (ADR-0057, no image fallback). One failed segment is not
     * that: it is dropped alone.
     */
    @Volatile
    var encoderUnavailable = false

    fun disableEncoder() {
      if (encoderUnavailable) return
      encoderUnavailable = true
      Log.w(TAG, "replay: no usable H.264 encoder on this device, session replay is off")
    }
  }
}

/**
 * Main thread. When the window last drew: every frame while something moves,
 * never on a still screen.
 */
private class WindowDraws : android.view.ViewTreeObserver.OnDrawListener {
  var lastDrawAt = 0L
    private set

  override fun onDraw() {
    lastDrawAt = SystemClock.uptimeMillis()
  }
}
