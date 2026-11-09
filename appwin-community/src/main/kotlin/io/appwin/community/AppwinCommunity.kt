package io.appwin.community

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.appwin.community.ui.CommunityPausedScreen
import io.appwin.community.ui.CommunityUnavailableScreen
import io.appwin.community.ui.HostProfileEditing
import io.appwin.community.ui.HostProfileHandlers
import io.appwin.community.ui.LocalHostOwnsBottomChrome
import io.appwin.community.ui.LocalProfileEditing
import io.appwin.community.ui.ProfileEditing
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinPause
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
  // The SDK version, Core's: the products release together (it said "0.1.0-dev" until 1.0.0).
  public const val VERSION: String = AppwinCore.VERSION

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var identityJob: Job? = null
  private var availabilityJob: Job? = null
  private var foregroundJob: Job? = null

  private val result = MutableStateFlow<AppwinInitResult?>(null)
  private val pauseState = MutableStateFlow<AppwinPause?>(null)

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
   * if (AppwinCommunity.initialize().isReady) {
   *   tabs += Tab.Community
   * }
   * ```
   *
   * Idempotent, and cheap after the first call: every product shares one
   * server round trip and its cached verdict.
   *
   * A ready result also makes Community reachable from its notifications, even
   * when no feed is on screen; set [onNotificationTap] **before** this call if
   * your app routes those taps itself.
   */
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val verdict = AppwinCore.availability(AppwinProduct.COMMUNITY)
    val paused = if (verdict.isReady) AppwinCore.pause(AppwinProduct.COMMUNITY) else null
    applyVerdict(verdict, paused)
    if (!verdict.isReady) {
      reportUnavailable(AppwinProduct.COMMUNITY, verdict)
    } else if (paused == null) {
      // On pause there is no push to register, so a missing token is no warning.
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
  private fun applyVerdict(verdict: AppwinInitResult, pause: AppwinPause?) {
    // A pause only means something on a ready product (the host keeps its tab).
    val paused = if (verdict.isReady) pause else null
    pauseState.value = paused
    result.value = verdict
    debugUnlocked.value =
      verdict.isReady && paused == null && AppwinCore.isDebugUnlocked(AppwinProduct.COMMUNITY)
    // A pause is "not ready" for everything running behind the screen: no push,
    // no watcher, no identity or foreground work.
    if (verdict.isReady && paused == null) {
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

  @OptIn(AppwinInternalApi::class)
  private fun followAvailability() {
    if (availabilityJob?.isActive == true) return
    availabilityJob = scope.launch {
      // One flow for both: two combined flows could show a new verdict with the old pause.
      AppwinCore.productStateFlow(AppwinProduct.COMMUNITY)
        .collect { applyVerdict(it.result, it.pause) }
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

  /**
   * The pause Community is held on, or `null` when it is not paused.
   *
   * While paused, [initialize] still returns [AppwinInitResult.Ready] so the
   * host keeps its tab; [CommunityView] and [presentCommunity] show the
   * "paused" screen instead of the feed.
   */
  @JvmStatic
  public val pause: AppwinPause?
    get() = pauseState.value

  /** Test-only: the object is a singleton, so one test would leak into the next. */
  internal fun resetForTesting() {
    listOf(availabilityJob, identityJob, foregroundJob).forEach { it?.cancel() }
    availabilityJob = null
    identityJob = null
    foregroundJob = null
    result.value = null
    pauseState.value = null
    debugUnlocked.value = false
    unregisterCommunityPush()
    CommunityInAppWatcher.stop()
  }

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
   * @param onEditProfile your own profile editor, replacing the SDK's: every
   *   place that would open it calls this instead, on the main thread. The
   *   photo and name callbacks are then unused.
   * @param onEditProfilePhoto your own photo picker: the SDK's editor still
   *   opens, and tapping its avatar calls this instead.
   * @param onEditProfileName your own name editor: the SDK's editor shows the
   *   name read-only and calls this when it is tapped. The anonymity switch is
   *   hidden, since anonymity would replace the name your app manages.
   *
   * Before calling one of these the SDK closes its editor, discarding what was
   * typed there. Once your app has saved, push the result with [setUser]. What
   * this view declared still holds after it goes away, so a post opened from a
   * notification never offers to edit a field your app owns.
   */
  @Composable
  public fun CommunityView(
    unavailable: (@Composable (AppwinInitResult?) -> Unit)? = null,
    hostOwnsBottomChrome: Boolean = false,
    onEditProfile: (() -> Unit)? = null,
    onEditProfilePhoto: (() -> Unit)? = null,
    onEditProfileName: (() -> Unit)? = null,
  ) {
    val handlers = remember(onEditProfile, onEditProfilePhoto, onEditProfileName) {
      HostProfileHandlers(onEditProfile, onEditProfilePhoto, onEditProfileName)
    }
    LaunchedEffect(handlers) { HostProfileEditing.declare(handlers) }
    val profileEditing = remember(handlers) { ProfileEditing(handlers) }
    val verdict by result.collectAsStateWithLifecycle()
    val current = verdict
    val paused by pauseState.collectAsStateWithLifecycle()
    // The host's own `unavailable` is never used for a pause: the host's tab
    // stays, and the SDK says why.
    paused?.takeIf { current?.isReady == true }?.let {
      CommunityPausedScreen(it)
      return
    }
    if (current?.isReady == true) {
      CompositionLocalProvider(
        LocalHostOwnsBottomChrome provides hostOwnsBottomChrome,
        LocalProfileEditing provides profileEditing,
      ) {
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
   *
   * The profile callbacks work as on [CommunityView]; before calling one, the
   * SDK also closes this full screen feed, so your screen is not opened behind it.
   */
  @JvmStatic
  @JvmOverloads
  public fun presentCommunity(
    context: Context,
    onEditProfile: (() -> Unit)? = null,
    onEditProfilePhoto: (() -> Unit)? = null,
    onEditProfileName: (() -> Unit)? = null,
  ) {
    if (!isReady) {
      reportNotReady(result.value)
      return
    }
    val paused = pause
    if (paused != null) {
      context.startActivity(CommunityActivity.pausedIntent(context, paused))
      return
    }
    val presentation = HostProfileEditing.present(
      HostProfileHandlers(onEditProfile, onEditProfilePhoto, onEditProfileName),
    )
    context.startActivity(CommunityActivity.presentIntent(context, presentation))
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
