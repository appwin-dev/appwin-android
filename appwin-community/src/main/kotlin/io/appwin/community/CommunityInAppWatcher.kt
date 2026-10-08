package io.appwin.community

import android.content.Context
import io.appwin.community.data.CommunityRepository
import io.appwin.community.domain.CommunityNotification
import io.appwin.community.domain.CommunityNotificationType
import io.appwin.community.ui.CommunityStrings
import io.appwin.community.ui.OpenPost
import io.appwin.community.ui.communityStrings
import io.appwin.core.AppwinCore
import io.appwin.core.inapp.AppwinBanner
import io.appwin.core.inapp.AppwinInAppBanner
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Turns an inbound community notification into an in-app banner.
 *
 * The gap this closes: the server deliberately *skips* the push notification
 * when the customer has a live realtime connection - it assumes something
 * in-app will take over (`CommunityPushService`). Until now nothing did, so a
 * member sitting in the app on any screen other than that post was told
 * nothing at all.
 *
 * Runs for the life of the process once Community is ready, not the life of
 * a screen: the feed also hears of activity, but only while it is mounted,
 * which is precisely when a banner is often still wanted (member on another
 * tab, or on Community's own feed rather than the target post).
 *
 * Mirrors `SupportInAppWatcher`.
 */
internal object CommunityInAppWatcher {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val subscriptions = mutableListOf<UUID>()

  private var repository: CommunityRepository? = null
  private var appContext: Context? = null
  private var started = false

  /**
   * Notifications older than this are not announced.
   *
   * Set when the watcher starts, so opening the app does not replay every
   * notification received while it was closed - those already went out as push.
   */
  private var lastNotifiedMillis = 0L

  fun start(context: Context, repository: CommunityRepository) {
    if (started) return
    val hub = AppwinCore.realtimeHub() ?: return
    started = true
    this.repository = repository
    this.appContext = context.applicationContext
    // Seed from the inbox watermark (not wall clock) so a skewed device
    // clock cannot make every fresh notification look "already seen".
    lastNotifiedMillis = Long.MAX_VALUE

    subscriptions += hub.on("community.notification.created") { check() }
    subscriptions += hub.onConnected { check() }
    hub.start()
    scope.launch {
      seedWatermark(repository)
      check()
    }
  }

  private suspend fun seedWatermark(repository: CommunityRepository) {
    val newest = runCatching { repository.notifications(limit = 20) }
      .getOrNull()
      ?.maxByOrNull { it.createdAtMillis }
      ?.createdAtMillis
    lastNotifiedMillis = when {
      newest != null && newest > 0L -> newest
      else -> System.currentTimeMillis()
    }
  }

  fun stop() {
    val hub = AppwinCore.realtimeHub()
    subscriptions.forEach { hub?.off(it) }
    subscriptions.clear()
    started = false
  }

  private fun check() {
    if (lastNotifiedMillis == Long.MAX_VALUE) return
    val repo = repository ?: return
    scope.launch {
      val newest = runCatching { repo.notifications(limit = 20) }
        .getOrNull()
        ?.maxByOrNull { it.createdAtMillis }
        ?: return@launch
      announceIfNew(newest)
    }
  }

  private fun announceIfNew(notification: CommunityNotification) {
    val at = notification.createdAtMillis
    if (at <= 0L || at <= lastNotifiedMillis) return
    // Already reading that post: the detail is the notification.
    if (notification.postId != null && notification.postId == OpenPost.postId) {
      lastNotifiedMillis = at
      return
    }
    lastNotifiedMillis = at

    val context = appContext ?: return
    val strings = communityStrings(context)
    val (title, body) = bannerCopy(notification, strings)
    if (body.isBlank()) return

    val postId = notification.postId

    AppwinCommunity.unreadCounts.refresh(requireSubscriber = false)
    AppwinInAppBanner.present(
      AppwinBanner(
        id = "community:${notification.id}",
        title = title,
        body = body,
        onTap = {
          if (postId != null) {
            // Inbox rows do not store the thread root; open the post.
            AppwinCommunity.openPost(context, postId)
          } else {
            AppwinCommunity.presentCommunity(context)
          }
        },
      ),
    )
  }

  /** Same shape as the server push copy (`CommunityPushService.copyFor`). */
  internal fun bannerCopy(
    notification: CommunityNotification,
    strings: CommunityStrings,
  ): Pair<String, String> {
    val someone = strings.notifSomeone
    val name = (notification.actorNickname?.trim().orEmpty()).ifBlank { someone }
    val excerpt = notification.excerpt?.trim()?.takeIf { it.isNotEmpty() }
    return when (notification.type) {
      CommunityNotificationType.POST_COMMENT ->
        strings.notifTitleComment to
          if (excerpt != null) strings.notifBodyCommentedExcerpt(name, excerpt)
          else strings.notifBodyCommented(name)
      CommunityNotificationType.COMMENT_REPLY ->
        strings.notifTitleReply to
          if (excerpt != null) strings.notifBodyRepliedExcerpt(name, excerpt)
          else strings.notifBodyReplied(name)
      CommunityNotificationType.POST_REACTION,
      CommunityNotificationType.COMMENT_REACTION,
      ->
        strings.notifTitleReaction to
          if (excerpt != null) strings.notifBodyLikedExcerpt(name, excerpt)
          else strings.notifBodyLiked(name)
      CommunityNotificationType.POLL_VOTE ->
        strings.notifTitlePoll to strings.notifBodyVoted(name)
      CommunityNotificationType.ADMIN_POST ->
        name to (excerpt ?: strings.notifBodyAdminPost)
      CommunityNotificationType.CONTENT_REMOVED,
      CommunityNotificationType.ACCOUNT_WARNED,
      CommunityNotificationType.ACCOUNT_BANNED,
      ->
        strings.title to name.take(80)
    }
  }
}
