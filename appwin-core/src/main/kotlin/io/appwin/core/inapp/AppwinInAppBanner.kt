package io.appwin.core.inapp

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * One in-app notification.
 *
 * [id] deduplicates: the same banner presented twice in a row is shown once.
 * [onTap] runs on the main thread, after the banner has been dismissed.
 */
public data class AppwinBanner(
  public val id: String,
  public val title: String,
  public val body: String,
  /** Studio accent, used for the leading dot. Defaults to a neutral grey. */
  public val accentArgb: Int? = null,
  public val onTap: (() -> Unit)? = null,
)

/**
 * The shared in-app notification surface, hosted by Core.
 *
 * Support and Notifications are sibling artefacts that both depend on Core and
 * neither on the other, so neither can own a surface the other needs. Putting
 * the banner here is the same call already made for `RealtimeHub`: one
 * connection, one overlay, fed by whichever products the studio bought.
 *
 * It draws with framework views on purpose. Core has no Compose dependency, and
 * a studio that only ships Analytics should not pull the Compose runtime in to
 * get a two-line banner.
 *
 * Installed by `AppwinCore.configure`; a host app never calls [install] itself.
 */
public object AppwinInAppBanner {

  /**
   * Set by whichever product currently owns the screen.
   *
   * The messenger raises it while it is open: a banner announcing the message
   * already visible in the thread underneath is noise.
   */
  @Volatile
  public var isSuppressed: Boolean = false

  private var currentActivity: WeakReference<Activity>? = null
  private var showing: View? = null
  private var lastShownId: String? = null
  private var installed = false

  /** Auto-dismiss delay. Long enough to read two lines, short enough to forgive. */
  private const val VISIBLE_MS = 5_000L
  private const val ANIMATION_MS = 220L

  @JvmStatic
  public fun install(application: Application) {
    if (installed) return
    installed = true
    application.registerActivityLifecycleCallbacks(object :
      Application.ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
      }

      override fun onActivityPaused(activity: Activity) {
        // The banner belongs to the activity it was attached to; leaving that
        // screen takes it with it rather than stranding it over the next one.
        if (currentActivity?.get() === activity) {
          dismiss()
          currentActivity = null
        }
      }

      override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    })
  }

  /**
   * Shows [banner], or drops it.
   *
   * Dropped when the app has no resumed activity - it is in the background, and
   * the push notification is what covers that case - when a product has
   * suppressed the surface, or when the same banner is already on screen.
   */
  @JvmStatic
  public fun present(banner: AppwinBanner) {
    val activity = currentActivity?.get() ?: return
    if (isSuppressed) return
    if (banner.id == lastShownId && showing != null) return

    activity.runOnUiThread {
      val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread
      dismissNow()
      lastShownId = banner.id
      val view = buildView(activity, banner)
      showing = view
      root.addView(view)
      animateIn(view)
      view.postDelayed({ if (showing === view) dismiss() }, VISIBLE_MS)
    }
  }

  /** Slides the current banner away, if there is one. */
  @JvmStatic
  public fun dismiss() {
    val view = showing ?: return
    showing = null
    view.animate()
      .translationY(-view.height.toFloat() - view.marginTopPx())
      .alpha(0f)
      .setDuration(ANIMATION_MS)
      .withEndAction { (view.parent as? ViewGroup)?.removeView(view) }
      .start()
  }

  private fun dismissNow() {
    val view = showing ?: return
    showing = null
    (view.parent as? ViewGroup)?.removeView(view)
  }

  private fun animateIn(view: View) {
    view.alpha = 0f
    // Measured lazily: the height is only known once the view has been laid out.
    view.post {
      view.translationY = -view.height.toFloat()
      view.animate().translationY(0f).alpha(1f).setDuration(ANIMATION_MS).start()
    }
  }

  private fun buildView(activity: Activity, banner: AppwinBanner): View {
    val dp = { value: Float ->
      TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        activity.resources.displayMetrics,
      ).toInt()
    }

    val card = LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
      background = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(16f).toFloat()
      }
      elevation = dp(8f).toFloat()
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.TOP,
      ).apply {
        val side = dp(12f)
        setMargins(side, statusBarInset(activity) + dp(8f), side, 0)
      }
      isClickable = true
    }

    card.addView(
      View(activity).apply {
        background = GradientDrawable().apply {
          shape = GradientDrawable.OVAL
          setColor(banner.accentArgb ?: Color.parseColor("#94A3B8"))
        }
        layoutParams = LinearLayout.LayoutParams(dp(8f), dp(8f)).apply {
          marginEnd = dp(10f)
        }
      },
    )

    val column = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    column.addView(
      TextView(activity).apply {
        text = banner.title
        setTextColor(Color.parseColor("#0E172A"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
      },
    )
    column.addView(
      TextView(activity).apply {
        text = banner.body
        setTextColor(Color.parseColor("#334156"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
      },
    )
    card.addView(column)

    attachGestures(card, banner)
    return card
  }

  /**
   * Tap opens, drag upwards dismisses.
   *
   * Written by hand rather than with a gesture detector: the only two gestures
   * are "did it move up far enough" and "did it not move at all", and a
   * detector would be more code than the two comparisons.
   */
  private fun attachGestures(view: View, banner: AppwinBanner) {
    var downY = 0f
    var dragged = false
    val slop = view.resources.displayMetrics.density * 8f

    view.setOnTouchListener { v, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downY = event.rawY
          dragged = false
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dy = event.rawY - downY
          if (dy < -slop) {
            dragged = true
            v.translationY = dy
          }
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          val dy = event.rawY - downY
          when {
            dy < -slop * 4 -> dismiss()
            dragged -> v.animate().translationY(0f).setDuration(ANIMATION_MS).start()
            event.actionMasked == MotionEvent.ACTION_UP -> {
              dismiss()
              banner.onTap?.invoke()
            }
          }
          true
        }

        else -> false
      }
    }
  }

  private fun View.marginTopPx(): Float =
    ((layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0).toFloat()

  /** Status bar height, so the banner clears it on every Android version. */
  private fun statusBarInset(activity: Activity): Int {
    val insets = activity.window?.decorView?.rootWindowInsets
    if (insets != null) {
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        insets.getInsets(WindowInsets.Type.statusBars()).top
      } else {
        @Suppress("DEPRECATION")
        insets.systemWindowInsetTop
      }
    }
    // Before the window is attached there are no insets to read; the resource
    // is what the platform itself falls back to.
    val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) activity.resources.getDimensionPixelSize(id) else 0
  }
}
