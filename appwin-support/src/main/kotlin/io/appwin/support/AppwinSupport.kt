package io.appwin.support

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import io.appwin.core.AppwinCore
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.AppwinUnavailableReason.DISABLED
import io.appwin.core.availability.AppwinUnavailableReason.DISABLED
import io.appwin.core.availability.reportUnavailable
import io.appwin.support.data.ApiSupportRepository
import io.appwin.support.data.SupportRepository
import io.appwin.support.domain.Customer
import io.appwin.support.domain.SupportUserAttributes
import io.appwin.support.ui.MessengerRoot

/**
 * Appwin Support SDK for Android.
 *
 * The whole interface is native and comes from the SDK: your app provides an
 * entry point, the SDK draws the help centre, the FAQ and the conversations.
 * Customisation - colours, agent name, welcome message, FAQ - goes through the
 * dashboard and is re-read on every open.
 *
 * The contract follows the iOS SDK, platform idioms aside.
 *
 * [AppwinCore.configure] must have been called first.
 */
public object AppwinSupport {
  public const val VERSION: String = "0.1.0-dev"

  /**
   * Prepares Support for this app, and says whether it may be used.
   *
   * Call it after [AppwinCore.configure] and **before** showing any Support entry point, then gate your
   * own UI on the result: the SDK cannot hide your button or your tab, it does
   * not own your navigation.
   *
   * ```kotlin
   * if (AppwinCore.availability(AppwinProduct.SUPPORT).isReady) {
   *   showHelpButton = true
   * }
   * ```
   *
   * Idempotent, and cheap after the first call: the three products share one
   * server round trip and its cached verdict.
   */
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.SUPPORT)
    isReady = result.isReady
    if (!result.isReady) {
      reportUnavailable(AppwinProduct.SUPPORT, result)
      SupportInAppWatcher.stop()
    } else {
      AppwinCore.reportMissingPushToken(AppwinProduct.SUPPORT)
      // From here on a reply announces itself wherever the customer is in the
      // app, not only inside the messenger. Lazy: the socket only opens when
      // this device has a conversation someone could actually reply to;
      // before that, the server notifies by push.
      context?.let { SupportInAppWatcher.startIfNeeded(it, repository) }
    }
    return result
  }

  /** Whether [initialize] has returned [AppwinInitResult.Ready]. */
  @JvmStatic
  public var isReady: Boolean = false
    private set

  private val repository: SupportRepository by lazy { ApiSupportRepository() }

  /** Watcher-only access: it starts from repository callbacks with no instance at hand. */
  internal val watcherRepository: SupportRepository get() = repository

  /** The messenger, to embed in your own navigation. */
  @Composable
  public fun MessengerView() {
    // A neutral view rather than a crash or a blank screen: this is the
    // embedded path, and it may already be mounted when a plan lapses.
    if (!isReady) {
      reportUnavailable(AppwinProduct.SUPPORT, AppwinInitResult.unavailable(DISABLED))
      return
    }
    // The embedded path never goes through `presentMessenger`, so this is where
    // the SDK gets a context to raise a banner from.
    rememberContext(androidx.compose.ui.platform.LocalContext.current)
    MessengerRoot(onClose = null)
  }

  /** The messenger as a sheet over your app, with its close button. */
  /**
   * Remembers the app context so the SDK can raise an in-app banner.
   *
   * `initialize()` is a suspend function with no context of its own, and the
   * banner has to be presentable from a realtime event with no screen involved.
   * Set by [presentMessenger] and by the composable entry point.
   */
  internal var context: Context? = null
    // Falls back to the context `configure` was given: a Flutter or React
    // Native host calls `initialize()` over a method channel with no screen
    // involved, and without this the watcher stayed unsubscribed until the
    // messenger was opened by hand - that is, until the banner was useless.
    get() = field ?: AppwinCore.applicationContext
    private set

  internal fun rememberContext(value: Context) {
    context = value.applicationContext
    if (isReady) SupportInAppWatcher.start(value.applicationContext, repository)
  }

  /**
   * Opens the messenger straight onto [conversationId].
   *
   * What the in-app banner and a push tap both need: landing on the home screen
   * after being told "you have a reply" makes the reader hunt for it.
   */
  @JvmStatic
  public fun presentConversation(context: Context, conversationId: String) {
    if (!isReady) {
      reportUnavailable(AppwinProduct.SUPPORT, AppwinInitResult.unavailable(DISABLED))
      return
    }
    context.startActivity(
      Intent(context, MessengerActivity::class.java)
        .putExtra(MessengerActivity.EXTRA_CONVERSATION_ID, conversationId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }

  @JvmStatic
  public fun presentMessenger(context: Context) {
    // Refused rather than half-presented: an activity that opens on an empty
    // screen is harder to diagnose than one that never opens and says why.
    if (!isReady) {
      reportUnavailable(AppwinProduct.SUPPORT, AppwinInitResult.unavailable(DISABLED))
      return
    }
    rememberContext(context)
    context.startActivity(Intent(context, MessengerActivity::class.java))
  }

  /**
   * Attaches the device to your app's user.
   *
   * The identity is owned by Core and shared by every product: after this call,
   * the same person is recognised by Community.
   */
  @JvmStatic
  public fun loginIdentifiedUser(externalId: String) {
    AppwinCore.identify(externalId)
  }

  /**
   * Enriches the current customer with what your app already knows.
   *
   * Does **not** change identity; that is [loginIdentifiedUser]. Every field is
   * optional and an omitted one is not overwritten.
   */
  @JvmStatic
  public suspend fun updateUser(attributes: SupportUserAttributes): Customer =
    repository.identify(attributes)

  /** Revokes the session and goes back to an anonymous customer. */
  @JvmStatic
  public suspend fun logout() {
    AppwinCore.signOut()
  }
}
