package io.appwin.community

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.data.ApiCommunityRepository
import io.appwin.community.data.CommunityRepository
import io.appwin.community.data.UnreadCountStore
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.events.AppwinCommunityEvents
import io.appwin.community.push.CommunityOpener
import io.appwin.community.push.CommunityPushHandler
import io.appwin.community.push.registerCommunityPush
import io.appwin.community.push.unregisterCommunityPush
import io.appwin.community.ui.CommunityRoot
import io.appwin.community.ui.CommunityUiRefresh
import io.appwin.community.ui.CommunityUnavailableScreen
import io.appwin.community.ui.LocalHostOwnsBottomChrome
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.reportUnavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Appwin Community SDK for Android.
 *
 * The whole interface is native and comes from the SDK: your app provides an
 * entry point - a tab, a button - and the SDK draws the feed, the comments and
 * the profiles. Customisation goes through the dashboard, not the code, and the
 * SDK re-reads its configuration on every open, so a studio-side change applies
 * without republishing the app.
 *
 * The contract mirrors the iOS SDK, platform idioms aside.
 *
 * [AppwinCore.configure] must have been called first.
 */
public object AppwinCommunity {
  public const val VERSION: String = "0.1.0-dev"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var identityJob: Job? = null
  private var availabilityJob: Job? = null
  private var foregroundJob: Job? = null

  private val result = MutableStateFlow<AppwinInitResult?>(null)

  /** Ready only thanks to a debuggable host: the feed warns that release builds will not get it. */
  internal val debugUnlocked = MutableStateFlow(false)

  private val pushHandler = CommunityPushHandler(
    onTarget = { context, target ->
      CommunityOpener.open(context, target, fromTap = true, hostTapHandler = { onNotificationTap })
    },
    onActivity = { unreadCounts.refresh() },
  )

  /**
   * Prepares Community for this app, and says whether it may be used.
   *
   * Call it after [AppwinCore.configure] and **before** mounting the feed, then gate your
   * own UI on the result: the SDK cannot hide your button or your tab, it does
   * not own your navigation.
   *
   * ```kotlin
   * if (AppwinCore.availability(AppwinProduct.COMMUNITY).isReady) {
   *   tabs += Tab.Community
   * }
   * ```
   *
   * Idempotent, and cheap after the first call: the three products share one
   * server round trip and its cached verdict.
   *
   * A ready result also makes Community reachable from its notifications, even
   * when no feed is on screen; set [onNotificationTap] **before** this call if
   * your app routes those taps itself.
   */
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val verdict = AppwinCore.availability(AppwinProduct.COMMUNITY)
    applyVerdict(verdict)
    if (!verdict.isReady) {
      reportUnavailable(AppwinProduct.COMMUNITY, verdict)
    } else {
      AppwinCore.reportMissingPushToken(AppwinProduct.COMMUNITY)
    }
    followAvailability()
    return verdict
  }

  /**
   * One place for the side effects of a verdict, whether it comes from
   * [initialize] or later from the live stream (a plan that lapses, a toggle).
   */
  @OptIn(AppwinInternalApi::class)
  private fun applyVerdict(verdict: AppwinInitResult) {
    result.value = verdict
    debugUnlocked.value = verdict.isReady && AppwinCore.isDebugUnlocked(AppwinProduct.COMMUNITY)
    if (verdict.isReady) {
      registerCommunityPush(pushHandler)
      observeIdentity()
      observeForeground()
      AppwinCore.applicationContext?.let { CommunityInAppWatcher.start(it, repository) }
    } else {
      // Kept by AppwinPush until Community is ready again, instead of opening
      // a post the member cannot load.
      unregisterCommunityPush()
      CommunityInAppWatcher.stop()
    }
  }

  private fun followAvailability() {
    if (availabilityJob?.isActive == true) return
    availabilityJob = scope.launch {
      AppwinCore.availabilityFlow(AppwinProduct.COMMUNITY).collect { applyVerdict(it) }
    }
  }

  @OptIn(AppwinInternalApi::class)
  private fun observeForeground() {
    if (foregroundJob?.isActive == true) return
    foregroundJob = scope.launch {
      AppwinCore.foregroundReturns.collect { unreadCounts.refresh() }
    }
  }

  /**
   * A feed mounted before an identify or a logout would keep the previous
   * member's avatar and posting rights.
   */
  @OptIn(AppwinInternalApi::class)
  private fun observeIdentity() {
    if (identityJob?.isActive == true) return
    identityJob = scope.launch {
      AppwinCore.identityChanges.collect { CommunityUiRefresh.request() }
    }
  }

  /** Whether the current verdict is [AppwinInitResult.Ready]. */
  @JvmStatic
  public val isReady: Boolean
    get() = result.value?.isReady == true

  /**
   * The current verdict for Community: the result of [initialize], then kept up
   * to date as the server's answer changes. `null` until [initialize] is called.
   */
  @JvmStatic
  public val lastResult: AppwinInitResult?
    get() = result.value

  private val repository: CommunityRepository by lazy { ApiCommunityRepository() }

  internal val unreadCounts: UnreadCountStore by lazy {
    UnreadCountStore(scope = scope, fetch = { repository.bootstrap().unreadNotificationCount })
  }

  /**
   * The feed, to embed in your own navigation - typically a tab.
   *
   * This is the expected integration: the view fills the space it is given and
   * shows no close button, since the tab is the way out.
   *
   * The view follows the verdict live: while Community is not ready it shows a
   * placeholder, and it swaps to the feed (or back) when the verdict changes,
   * without your app remounting it. Debug builds of your app add a card to the
   * placeholder that says why Community is closed and how to fix it.
   *
   * @param unavailable your own UI for the not-ready case, given the current
   *   verdict (`null` when [initialize] was never called). The SDK's
   *   placeholder is used when omitted.
   * @param hostOwnsBottomChrome when the host draws a tab bar (or other bottom
   *   chrome) under this view. Community then skips navigation-bar padding so
   *   the compose FAB does not float above an empty band.
   */
  @Composable
  public fun CommunityView(
    unavailable: (@Composable (AppwinInitResult?) -> Unit)? = null,
    hostOwnsBottomChrome: Boolean = false,
  ) {
    val verdict by result.collectAsStateWithLifecycle()
    val current = verdict
    if (current?.isReady == true) {
      CompositionLocalProvider(LocalHostOwnsBottomChrome provides hostOwnsBottomChrome) {
        CommunityRoot(onClose = null)
      }
      return
    }
    LaunchedEffect(current) { reportNotReady(current) }
    if (unavailable != null) unavailable(current) else CommunityUnavailableScreen(current)
  }

  private fun reportNotReady(verdict: AppwinInitResult?) {
    if (verdict != null) {
      reportUnavailable(AppwinProduct.COMMUNITY, verdict)
    } else {
      Log.w("Appwin", "community is not available: AppwinCommunity.initialize() has not been called.")
    }
  }

  /**
   * The feed full screen, with its close button.
   *
   * For apps where the community has no dedicated tab: a menu entry, or an
   * open from a notification.
   */
  @JvmStatic
  public fun presentCommunity(context: Context) {
    if (!isReady) {
      reportNotReady(result.value)
      return
    }
    context.startActivity(Intent(context, CommunityActivity::class.java))
  }

  /**
   * Opens a post, optionally with the reply thread under one of its comments.
   *
   * When a feed is mounted (your Community tab, even while you are switching to
   * it), the post opens in that feed; otherwise it opens full screen over your
   * app. The usual caller is your own [onNotificationTap] handler, after it has
   * selected the Community tab. Does nothing (and logs) while Community is not
   * ready.
   */
  @JvmStatic
  @JvmOverloads
  public fun openPost(context: Context, postId: String, commentId: String? = null) {
    if (!isReady) {
      reportNotReady(result.value)
      return
    }
    CommunityOpener.open(
      context = context,
      target = AppwinCommunityPostTarget(postId, commentId),
      fromTap = false,
      hostTapHandler = { null },
    )
  }

  /**
   * Takes over the navigation when the member taps a Community notification.
   *
   * Unset (the default), the SDK opens the post in the feed on screen, or full
   * screen over your app when the feed is not visible (for instance in another
   * tab). Set, the SDK does not navigate: it calls this on the main thread, and
   * your app typically selects its Community tab then calls [openPost].
   *
   * Set it **before** [initialize]: a tap that launched the app is replayed
   * right after that call.
   */
  @JvmStatic
  @Volatile
  public var onNotificationTap: ((AppwinCommunityPostTarget) -> Unit)? = null

  /**
   * Takes over profile editing (nickname, photo, bio).
   *
   * When set, every place in the SDK that would open its own profile editor
   * calls this instead, on the main thread. Push the result back with
   * [setUser]: the mounted feed refreshes and [events] reports
   * [AppwinCommunityEvent.ProfileUpdated]. Unset, the SDK's editor is used.
   */
  @JvmStatic
  @Volatile
  public var onEditProfile: (() -> Unit)? = null

  /**
   * What the current member does in Community (posts, comments, replies,
   * reactions, profile changes), once the server has confirmed it.
   *
   * Hot and without replay: collect it for as long as you want to hear about
   * actions, typically from app start. A collector that falls far behind loses
   * the oldest events.
   */
  @JvmStatic
  public val events: SharedFlow<AppwinCommunityEvent>
    get() = AppwinCommunityEvents.events

  /**
   * Enriches the community profile with what your app already knows.
   *
   * Does **not** change identity; that is [AppwinCore.identify]. Every field
   * is optional and an omitted one is not overwritten, so an app that only
   * knows a nickname does not erase the bio typed inside the SDK.
   *
   * Call [AppwinCore.identify] **first**, otherwise the attributes land on the
   * anonymous profile and are lost when the user is attached.
   */
  @JvmStatic
  @JvmOverloads
  public suspend fun setUser(
    nickname: String? = null,
    avatarUrl: String? = null,
    bio: String? = null,
  ): CommunityProfile = repository.setUser(nickname, avatarUrl, bio).also {
    CommunityUiRefresh.request()
    AppwinCommunityEvents.emit(AppwinCommunityEvent.ProfileUpdated(it.id))
  }

  /**
   * Unread notification count, for a tab badge.
   *
   * Returns `0` rather than failing: a badge must never break the rendering of
   * a tab bar.
   */
  @JvmStatic
  public suspend fun unreadNotificationCount(): Int =
    runCatching { repository.bootstrap().unreadNotificationCount }
      .onSuccess(unreadCounts::update)
      .getOrDefault(0)

  /**
   * Unread notification count, live, for a tab badge.
   *
   * Emits the last known value straight away when there is one, refreshes it
   * from the server when collection starts, then emits each change (never the
   * same value twice). Refreshed as the SDK hears of new activity: Community
   * pushes, taps, the app returning to the foreground, the feed reloading. At
   * most one extra request every 30 seconds, and none while nobody collects.
   */
  @JvmStatic
  public val unreadNotificationCountFlow: Flow<Int>
    get() = unreadCounts.updates
}
