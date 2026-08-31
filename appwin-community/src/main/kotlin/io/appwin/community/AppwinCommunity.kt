package io.appwin.community

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import io.appwin.community.data.ApiCommunityRepository
import io.appwin.community.data.CommunityRepository
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.ui.CommunityRoot
import io.appwin.core.AppwinCore
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.AppwinUnavailableReason.DISABLED
import io.appwin.core.availability.AppwinUnavailableReason.DISABLED
import io.appwin.core.availability.reportUnavailable

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
   */
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.COMMUNITY)
    isReady = result.isReady
    if (!result.isReady) reportUnavailable(AppwinProduct.COMMUNITY, result)
    return result
  }

  /** Whether [initialize] has returned [AppwinInitResult.Ready]. */
  @JvmStatic
  public var isReady: Boolean = false
    private set

  private val repository: CommunityRepository by lazy { ApiCommunityRepository() }

  /**
   * The feed, to embed in your own navigation - typically a tab.
   *
   * This is the expected integration: the view fills the space it is given and
   * shows no close button, since the tab is the way out.
   */
  @Composable
  public fun CommunityView() {
    if (!isReady) {
      reportUnavailable(AppwinProduct.COMMUNITY, AppwinInitResult.unavailable(DISABLED))
      return
    }
    CommunityRoot(onClose = null)
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
      reportUnavailable(AppwinProduct.COMMUNITY, AppwinInitResult.unavailable(DISABLED))
      return
    }
    context.startActivity(Intent(context, CommunityActivity::class.java))
  }

  /**
   * Attaches the member to your app's user.
   *
   * The identity is owned by Core and **shared by every product**: after this
   * call, the same person is recognised by Support.
   *
   * Two steps, not one: [AppwinCore.identify] records the `externalId` locally,
   * [AppwinCore.bootstrapSession] mints a token that carries it. Without the
   * second, the open session stays the anonymous one until the app next starts,
   * and the member's posts would go out under their old identity with nothing
   * to signal it.
   *
   * Hence `suspend`: the attachment is only acquired when the call returns.
   */
  @JvmStatic
  public suspend fun login(externalId: String) {
    require(externalId.isNotEmpty()) { "externalId must not be empty" }
    AppwinCore.identify(externalId)
    AppwinCore.bootstrapSession(externalId = externalId)
  }

  /** Revokes the session and goes back to an anonymous profile. */
  @JvmStatic
  public suspend fun logout() {
    AppwinCore.signOut()
  }

  /**
   * Enriches the community profile with what your app already knows.
   *
   * Does **not** change identity; that is [login]. Every field is optional and
   * an omitted one is not overwritten, so an app that only knows a nickname
   * does not erase the bio typed inside the SDK.
   *
   * Call [login] **first**, otherwise the attributes land on the anonymous
   * profile and are lost when the user is attached.
   */
  @JvmStatic
  @JvmOverloads
  public suspend fun setUser(
    nickname: String? = null,
    avatarUrl: String? = null,
    bio: String? = null,
  ): CommunityProfile = repository.setUser(nickname, avatarUrl, bio)

  /**
   * Unread notification count, for a tab badge.
   *
   * Returns `0` rather than failing: a badge must never break the rendering of
   * a tab bar.
   */
  @JvmStatic
  public suspend fun unreadNotificationCount(): Int =
    runCatching { repository.bootstrap().unreadNotificationCount }.getOrDefault(0)
}
