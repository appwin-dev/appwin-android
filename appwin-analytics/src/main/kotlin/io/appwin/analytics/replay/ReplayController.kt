@file:OptIn(AppwinInternalApi::class)

package io.appwin.analytics.replay

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.analytics.AnalyticsConsent
import io.appwin.core.analytics.AppwinAnalyticsHost
import io.appwin.core.availability.AppwinInitStatus
import io.appwin.core.availability.AppwinProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide owner of the recorder, driven by the `AppwinAnalytics` façade. */
internal object ReplayController {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val main by lazy { Handler(Looper.getMainLooper()) }

  @Volatile
  private var started = false

  @Volatile
  private var recorder: ReplayRecorder? = null

  @Volatile
  private var lastScreen: String? = null

  @Volatile
  var runtime: String = ReplayRuntime.ANDROID

  /**
   * Window pixels, from a bridge that draws without views (Flutter). Null
   * until its first report.
   */
  @Volatile
  var bridgedMasks: List<Rect>? = null
    set(value) {
      field = value
      bridgedAt = SystemClock.elapsedRealtime()
    }

  @Volatile
  private var bridgedAt = 0L

  /** The rules of the running recording, for a bridge that masks what it draws. */
  val bridgeConfig: ReplayConfig?
    get() = recorder?.takeIf { it.isRunning }?.config

  val isRecording: Boolean
    get() = recorder?.isRunning == true

  /**
   * After analytics is ready. Replay has its own toggle in the same
   * availability verdict, so this costs no extra round trip in release.
   */
  fun start() {
    if (started) return
    started = true
    scope.launch {
      val context = AppwinCore.applicationContext ?: return@launch
      val projectAppId = AppwinCore.projectAppId ?: return@launch
      val storage = ReplayStorage(context, projectAppId)
      val result = AppwinCore.availability(AppwinProduct.REPLAY)
      if (!result.isReady) {
        // A definite no: whatever an earlier run recorded will never be accepted.
        if (result.status == AppwinInitStatus.UNAVAILABLE) storage.purge()
        return@launch
      }
      val config = ReplayConfig.from(AppwinAnalyticsHost.productConfig(AppwinProduct.REPLAY))
      main.post {
        val created = ReplayRecorder(context, storage, config, { runtime }, ::masksFromBridge, scope)
        recorder = created
        lastScreen?.let(created::onScreen)
        created.start()
      }
    }
  }

  /**
   * Flutter draws without views: until its plugin reports, nothing of the
   * window may show, whatever the rules. The plugin resends every second, so
   * a set older than [BRIDGED_MAX_AGE_MS] may describe a screen gone since.
   */
  private fun masksFromBridge(): List<Rect> {
    if (runtime != "flutter") return bridgedMasks.orEmpty()
    val masks = bridgedMasks
    val fresh = SystemClock.elapsedRealtime() - bridgedAt <= BRIDGED_MAX_AGE_MS
    return if (masks != null && fresh) masks else listOf(Rect(0, 0, 100_000, 100_000))
  }

  private const val BRIDGED_MAX_AGE_MS = 2_500L

  /** The studio opted out in code: nothing left over from a previous build is kept. */
  fun optOut() {
    scope.launch {
      val context = AppwinCore.applicationContext ?: return@launch
      val projectAppId = AppwinCore.projectAppId ?: return@launch
      ReplayStorage(context, projectAppId).purge()
    }
  }

  fun onScreen(name: String) {
    lastScreen = name
    recorder?.onScreen(name)
  }

  fun onConsentChanged(consent: AnalyticsConsent) {
    recorder?.onConsentChanged(consent)
  }
}
