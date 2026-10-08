package io.appwin.analytics.replay

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import io.appwin.analytics.R

/**
 * Rectangles to paint over a frame, in window pixels, read from the view
 * hierarchy at capture time. Main thread only.
 *
 * Text inputs are always masked. Web views are masked whole unless
 * unmasked. An explicit mask always wins; an explicit unmask (inherited by
 * descendants) lifts the text, image and web view rules, never the input
 * rule.
 */
internal object ViewMasking {
  /**
   * React Native renders real views; some text classes are not TextViews.
   * Matched by name: the SDK does not depend on React Native.
   */
  private val TEXT_CLASSES = setOf(
    "com.facebook.react.views.text.ReactTextView",
    "com.facebook.react.views.text.PreparedLayoutTextView",
  )
  private val INPUT_CLASSES = setOf("com.facebook.react.views.textinput.ReactEditText")

  fun collect(root: View, config: ReplayConfig, out: MutableList<Rect>) {
    val clip = Rect(0, 0, root.width, root.height)
    walk(root, clip, inheritedUnmask = false, config, out, IntArray(2))
  }

  private fun walk(
    view: View,
    clip: Rect,
    inheritedUnmask: Boolean,
    config: ReplayConfig,
    out: MutableList<Rect>,
    location: IntArray,
  ) {
    if (view.visibility != View.VISIBLE || view.alpha <= 0f) return
    if (view.width <= 0 || view.height <= 0) return
    view.getLocationInWindow(location)
    val rect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    if (!rect.intersect(clip)) return

    val tag = view.getTag(R.id.appwin_replay_mask) as? Boolean
    val unmasked = tag == false || (tag == null && inheritedUnmask)
    val name = view.javaClass.name
    when {
      view is WebView -> {
        // Its content is not views: its form fields cannot be told apart, so
        // the whole page is masked unless the app unmasks it.
        if (!unmasked) out.add(rect)
        return
      }
      view is EditText || name in INPUT_CLASSES -> {
        out.add(rect)
        return
      }
      tag == true -> {
        out.add(rect)
        return
      }
      ComposeMasking.isComposeRoot(view) -> {
        ComposeMasking.collect(view, rect, unmasked, config, out)
      }
      view is TextView || name in TEXT_CLASSES -> {
        if (config.maskAllText && !unmasked) out.add(rect)
      }
      view is ImageView -> {
        if (config.maskAllImages && !unmasked) out.add(rect)
      }
    }

    if (view is ViewGroup) {
      val childClip = if (view.clipChildren) rect else clip
      for (index in 0 until view.childCount) {
        walk(view.getChildAt(index), childClip, unmasked, config, out, location)
      }
    }
  }
}
