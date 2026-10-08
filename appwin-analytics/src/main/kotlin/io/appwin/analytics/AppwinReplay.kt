@file:OptIn(AppwinInternalApi::class)

package io.appwin.analytics

import android.graphics.Rect
import android.view.View
import io.appwin.analytics.replay.ReplayController
import io.appwin.analytics.replay.ReplayRuntime
import io.appwin.core.AppwinInternalApi

/**
 * Session replay (ADR-0057): the analytics session recorded as a video, to
 * watch in the dashboard. Nothing to call to start it: it records once
 * [AppwinAnalytics.initialize] has run and the studio switched replay on for
 * the app, unless `initialize(sessionReplay = false)`.
 *
 * Masking happens on the device, before encoding. Text fields are always
 * masked, and web views unless unmasked. Other text and images follow the
 * project settings (`maskAllText`, `maskAllImages`), shown by default. Use
 * [mask] and [unmask] to adjust one view and its descendants; in Compose,
 * `Modifier.appwinMask()` and `Modifier.appwinUnmask()`.
 */
public object AppwinReplay {
  /** Hides [view] and everything inside it in replays. */
  @JvmStatic
  public fun mask(view: View) {
    view.setTag(R.id.appwin_replay_mask, true)
  }

  /**
   * Shows [view] and its descendants even when the project masks all text or
   * all images, and shows a web view. Text fields stay masked regardless.
   */
  @JvmStatic
  public fun unmask(view: View) {
    view.setTag(R.id.appwin_replay_mask, false)
  }

  /** Whether frames are being captured in this process right now. */
  @JvmStatic
  public val isRecording: Boolean
    get() = ReplayController.isRecording

  /**
   * For Appwin's Flutter and React Native plugins: the runtime reported with
   * each segment (`flutter`, `react_native`). Unknown values are ignored.
   */
  @AppwinInternalApi
  @JvmStatic
  public fun setBridgeRuntime(runtime: String) {
    ReplayRuntime.normalize(runtime)?.let { ReplayController.runtime = it }
  }

  /**
   * For Appwin's Flutter plugin, which draws without native views: extra
   * rectangles to mask, in window pixels, replacing the previous set. With
   * the `flutter` runtime the whole window is masked until a set arrives,
   * and again once the last one is older than 2.5 s: send every second.
   */
  @AppwinInternalApi
  @JvmStatic
  public fun setBridgedMasks(rects: List<Rect>) {
    ReplayController.bridgedMasks = rects.map(::Rect)
  }

  /**
   * For Appwin's Flutter plugin: `maskAllText` and `maskAllImages` of the
   * running recording, null when nothing records in this process.
   */
  @AppwinInternalApi
  @JvmStatic
  public val bridgeMaskRules: Map<String, Boolean>?
    get() = ReplayController.bridgeConfig?.let {
      mapOf("maskAllText" to it.maskAllText, "maskAllImages" to it.maskAllImages)
    }
}
