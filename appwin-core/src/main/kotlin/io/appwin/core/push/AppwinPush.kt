@file:OptIn(AppwinInternalApi::class)

package io.appwin.core.push

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.appwin.core.AppwinInternalApi

/**
 * What a product does with its pushes. Registered by the product itself, at
 * `initialize()`, through [AppwinPush.register].
 */
@AppwinInternalApi
public interface AppwinPushHandler {
  /**
   * The user tapped a push this product owns, or one whose deeplink routes to
   * it. Called on the thread that reported the tap: long work goes to a
   * coroutine.
   */
  public fun onTap(context: Context, payload: AppwinPushPayload)

  /** The app is in the foreground. True when the product showed its own UI instead. */
  public fun onForeground(context: Context, payload: AppwinPushPayload): Boolean = false

  /** A data or silent message. True when consumed. */
  public fun onMessage(context: Context, payload: AppwinPushPayload): Boolean = false
}

/**
 * The one entry point for Appwin pushes, whoever owns the push stack.
 *
 * With `appwin-notifications` and no push code of your own, nothing to do: the
 * SDK's `AppwinFirebaseMessagingService` and the tap handling below already
 * call it. If your app has its own `FirebaseMessagingService` (FlutterFire,
 * another vendor), drop ours and forward to these four calls:
 *
 * ```xml
 * <!-- AndroidManifest.xml, with xmlns:tools declared -->
 * <service
 *   android:name="io.appwin.notifications.AppwinFirebaseMessagingService"
 *   tools:node="remove" />
 * ```
 *
 * ```kotlin
 * class MyMessagingService : FirebaseMessagingService() {
 *   override fun onMessageReceived(message: RemoteMessage) {
 *     if (AppwinPush.handleMessage(this, message.data)) return
 *     val n = message.notification
 *     if (AppwinPush.handleForeground(this, message.data, n?.title, n?.body)) return
 *     // not Appwin's, or Appwin let you show it: your own display code
 *   }
 * }
 *
 * class MainActivity : Activity() {
 *   // A tap on a notification the system displayed launches this activity
 *   // with the push data as extras.
 *   override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     AppwinPush.handleTap(this, intent)
 *   }
 * }
 * ```
 *
 * The launch intent itself is read automatically once `AppwinCore.configure`
 * has run; calling [handleTap] on it too is harmless (a tap is routed once).
 * A tap reported before the owning product is initialized is kept and
 * replayed when it is.
 */
public object AppwinPush {
  private const val TAG = "Appwin"
  private const val MAX_PENDING = 10
  private const val NOTIFICATIONS = "notifications"

  /** A tap routed twice (auto intent reading plus a host forwarding) must open once. */
  private const val DUPLICATE_WINDOW_MS = 10_000L

  private val lock = Any()
  private val handlers = HashMap<String, AppwinPushHandler>()
  private val pending = ArrayDeque<Pair<String, AppwinPushPayload>>()
  private val warnedProducts = HashSet<String>()
  private var lastTapKey: String? = null
  private var lastTapAt = 0L
  private var appContext: Context? = null
  private var installed = false

  internal var clock: () -> Long = System::currentTimeMillis
  internal var urlOpener: (Context, String) -> Unit = ::openWithSystem

  /** Whether [data] (FCM `data`, or launch intent extras) is an Appwin push. */
  @JvmStatic
  public fun isAppwinPush(data: Map<String, String>): Boolean = AppwinPushPayload.parse(data) != null

  /**
   * Routes a tap on a notification.
   *
   * @return true when it was an Appwin push; it is then handled, or kept until
   *   the product that owns it is initialized.
   */
  @JvmStatic
  public fun handleTap(context: Context, data: Map<String, String>): Boolean {
    val payload = AppwinPushPayload.parse(data) ?: return false
    remember(context)
    if (isDuplicateTap(payload)) return true

    val deeplink = payload.deeplink
    if (deeplink != null && AppwinPushPayload.routeOf(deeplink) == null) {
      // Opened now, not when the product initializes: the host is not
      // brought up for an external link, so that moment may never come.
      urlOpener(context, deeplink)
    }
    val targets = linkedSetOf(payload.product)
    payload.deeplinkProduct?.let(targets::add)
    // `deliveryId` is campaign tracking (contract), which Notifications owns,
    // even when the campaign links into another product.
    if (payload.deliveryId != null) targets.add(NOTIFICATIONS)
    targets.forEach { dispatchTap(it, payload) }
    return true
  }

  /**
   * [handleTap] for an intent carrying the push data as extras, which is how a
   * notification displayed by the system (app in background) reaches the
   * launcher activity. The Appwin extras are removed once read, so the same
   * intent seen again on the next resume is not routed twice.
   */
  @JvmStatic
  public fun handleTap(context: Context, intent: Intent?): Boolean {
    // Extras parcelled by another app can fail to unmarshal; that is not a push.
    if (intent == null) return false
    val data = runCatching { intent.extras?.toStringMap() }.getOrNull() ?: return false
    if (!handleTap(context, data)) return false
    AppwinPushPayload.APPWIN_KEYS.forEach(intent::removeExtra)
    return true
  }

  /**
   * A push arrived while the app is in the foreground.
   *
   * @return true when Appwin showed its own UI (for instance the Support in-app
   *   banner) or the push has nothing to show: do not post a system
   *   notification then.
   */
  @JvmStatic
  @JvmOverloads
  public fun handleForeground(
    context: Context,
    data: Map<String, String>,
    title: String? = null,
    body: String? = null,
  ): Boolean {
    val payload = AppwinPushPayload.parse(data, title, body) ?: return false
    remember(context)
    if (payload.isSilent) return handleMessage(context, data)
    val handler = synchronized(lock) { handlers[payload.product] } ?: return false
    return handler.onForeground(context.applicationContext, payload)
  }

  /**
   * A data or silent message (for instance `inapp.pending`).
   *
   * @return true when consumed. A silent Appwin push is always consumed: there
   *   is nothing in it to display.
   */
  @JvmStatic
  public fun handleMessage(context: Context, data: Map<String, String>): Boolean {
    val payload = AppwinPushPayload.parse(data) ?: return false
    remember(context)
    val handler = synchronized(lock) { handlers[payload.product] }
    val consumed = handler?.onMessage(context.applicationContext, payload) ?: false
    return consumed || payload.isSilent
  }

  /** Makes [product] reachable, and replays the taps it missed. */
  @AppwinInternalApi
  @JvmStatic
  public fun register(product: String, handler: AppwinPushHandler) {
    val replay = synchronized(lock) {
      handlers[product] = handler
      val mine = pending.filter { it.first == product }
      pending.removeAll { it.first == product }
      mine.map { it.second }
    }
    val context = appContext ?: return
    replay.forEach { handler.onTap(context, it) }
  }

  @AppwinInternalApi
  @JvmStatic
  public fun unregister(product: String) {
    synchronized(lock) { handlers.remove(product) }
  }

  /**
   * Opens a deeplink: `appwin://<product>/...` goes to that product (kept until
   * it registers), anything else to the system. `appwin://` never reaches the
   * system: no app declares that scheme.
   */
  @AppwinInternalApi
  @JvmStatic
  public fun openDeeplink(context: Context, url: String) {
    val route = AppwinPushPayload.routeOf(url)
    if (route == null) {
      urlOpener(context, url)
      return
    }
    remember(context)
    dispatchTap(
      route.product,
      AppwinPushPayload(
        type = null,
        product = route.product,
        deeplink = url,
        deliveryId = null,
        imageUrl = null,
        title = null,
        body = null,
        raw = mapOf(AppwinPushPayload.KEY_DEEPLINK to url),
      ),
    )
  }

  /**
   * Reads the push data a system-displayed notification leaves on the launch
   * intent, for every activity, so a host without push code of its own has
   * nothing to forward. Installed by `AppwinCore.configure`.
   */
  internal fun install(application: Application) {
    remember(application)
    if (installed) return
    installed = true
    application.registerActivityLifecycleCallbacks(object :
      Application.ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) {
        handleTap(activity, activity.intent)
      }

      override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    })
  }

  private fun dispatchTap(product: String, payload: AppwinPushPayload) {
    val handler = synchronized(lock) {
      handlers[product] ?: run {
        if (pending.size >= MAX_PENDING) pending.removeFirst()
        pending.addLast(product to payload)
        null
      }
    }
    if (handler == null) {
      warnPending(product)
      return
    }
    val context = appContext ?: return
    handler.onTap(context, payload)
  }

  private fun warnPending(product: String) {
    val first = synchronized(lock) { warnedProducts.add(product) }
    if (!first) return
    Log.i(
      TAG,
      "Push tap kept for '$product': it is replayed once that product is " +
        "initialized (call its initialize() at launch).",
    )
  }

  private fun isDuplicateTap(payload: AppwinPushPayload): Boolean {
    val key = listOf(payload.type, payload.deeplink, payload.deliveryId).joinToString("|")
    val now = clock()
    synchronized(lock) {
      if (key == lastTapKey && now - lastTapAt < DUPLICATE_WINDOW_MS) return true
      lastTapKey = key
      lastTapAt = now
    }
    return false
  }

  private fun remember(context: Context) {
    if (appContext == null) appContext = context.applicationContext
  }

  private fun openWithSystem(context: Context, url: String) {
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }.onFailure { Log.w(TAG, "No app can open the push deeplink $url", it) }
  }

  @Suppress("DEPRECATION")
  private fun Bundle.toStringMap(): Map<String, String> {
    val map = HashMap<String, String>()
    for (key in keySet()) {
      (get(key) as? String)?.let { map[key] = it }
    }
    return map
  }

  /** Test-only: the object is a singleton, so one test would leak into the next. */
  internal fun resetForTesting() {
    synchronized(lock) {
      handlers.clear()
      pending.clear()
      warnedProducts.clear()
      lastTapKey = null
      lastTapAt = 0L
    }
    appContext = null
    clock = System::currentTimeMillis
    urlOpener = ::openWithSystem
  }
}
