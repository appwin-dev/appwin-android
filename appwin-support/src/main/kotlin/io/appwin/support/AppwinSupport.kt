package io.appwin.support

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.AppwinUnavailableReason.DISABLED
import io.appwin.core.availability.reportUnavailable
import io.appwin.core.push.AppwinPush
import io.appwin.support.data.ApiSupportRepository
import io.appwin.support.data.SupportRepository
import io.appwin.support.domain.Customer
import io.appwin.support.ui.MessengerRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val identifyMutex = Mutex()
  private var presentJob: Job? = null
  private var identityJob: Job? = null
  private var customer: Customer? = null

  /**
   * Prepares Support for this app, and says whether it may be used.
   *
   * Call it after [AppwinCore.configure] when you want to gate your own entry
   * point. [presentMessenger] also calls it on demand if you have not: a CTA
   * can fire straight into the sheet without a prior [initialize].
   *
   * Idempotent, and cheap after the first call: the three products share one
   * server round trip and its cached verdict.
   */
  @OptIn(AppwinInternalApi::class)
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.SUPPORT)
    isReady = result.isReady
    if (!result.isReady) {
      reportUnavailable(AppwinProduct.SUPPORT, result)
      SupportInAppWatcher.stop()
      AppwinPush.unregister(AppwinProduct.SUPPORT.key)
    } else {
      AppwinCore.reportMissingPushToken(AppwinProduct.SUPPORT)
      // Replays a push tap that launched the app before this ran.
      AppwinPush.register(AppwinProduct.SUPPORT.key, SupportPushHandler)
      // From here on a reply announces itself wherever the customer is in the
      // app, not only inside the messenger. Lazy: the socket only opens when
      // this device has a conversation someone could actually reply to;
      // before that, the server notifies by push.
      context?.let { SupportInAppWatcher.startIfNeeded(it, repository) }
      observeIdentity()
    }
    return result
  }

  /**
   * The customer shown in the messenger follows Core's identity: after an
   * identify or a logout the cached one belongs to someone else.
   */
  @OptIn(AppwinInternalApi::class)
  private fun observeIdentity() {
    if (identityJob?.isActive == true) return
    identityJob = scope.launch {
      AppwinCore.identityChanges.collect {
        customer = null
        runCatching { refreshCustomer() }
      }
    }
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
    rememberContext(context)
    if (presentJob?.isActive == true) return
    presentJob = scope.launch {
      try {
        openMessenger(context, conversationId)
      } finally {
        presentJob = null
      }
    }
  }

  /**
   * Opens the messenger over the host app.
   *
   * Safe to call from a host button without gating on [initialize] first: if
   * Support is not ready yet, this runs initialization, opens when it can, and
   * otherwise reports why via [reportUnavailable].
   */
  @JvmStatic
  public fun presentMessenger(context: Context) {
    rememberContext(context)
    if (presentJob?.isActive == true) return
    presentJob = scope.launch {
      try {
        openMessenger(context, conversationId = null)
      } finally {
        presentJob = null
      }
    }
  }

  private suspend fun openMessenger(context: Context, conversationId: String?) {
    val result = if (isReady) AppwinInitResult.Ready else initialize()
    if (!result.isReady) {
      reportUnavailable(AppwinProduct.SUPPORT, result)
      return
    }
    runCatching { ensureCustomer() }.getOrElse {
      android.util.Log.w("AppwinSupport", "presentMessenger: ensureCustomer failed", it)
      return
    }

    val intent = Intent(context, MessengerActivity::class.java)
    if (conversationId != null) {
      intent.putExtra(MessengerActivity.EXTRA_CONVERSATION_ID, conversationId)
    }
    if (context !is Activity) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  /**
   * The customer behind the current Core session, fetched once. The server
   * creates the lead on first call, so opening the messenger after
   * [AppwinCore.configure] alone works.
   */
  @OptIn(AppwinInternalApi::class)
  internal suspend fun ensureCustomer(): Customer {
    customer?.let { return it }
    return identifyMutex.withLock {
      customer?.let { return it }
      AppwinCore.bootstrapSession()
      refreshCustomer()
    }
  }

  /** Re-fetches the customer (language may change after inbound detection). */
  internal suspend fun refreshCustomer(): Customer {
    val next = repository.fetchCustomer()
    customer = next
    return next
  }

  /** Recognised language for UI date stamps, or null to use the device locale. */
  @JvmStatic
  public fun currentCustomerLanguage(): String? = customer?.language
}
