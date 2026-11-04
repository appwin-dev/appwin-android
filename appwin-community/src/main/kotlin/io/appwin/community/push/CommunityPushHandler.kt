@file:OptIn(AppwinInternalApi::class)

package io.appwin.community.push

import android.content.Context
import io.appwin.community.AppwinCommunityPostTarget
import io.appwin.community.ui.OpenPost
import io.appwin.community.ui.communityStrings
import io.appwin.core.AppwinInternalApi
import io.appwin.core.inapp.AppwinBanner
import io.appwin.core.inapp.AppwinInAppBanner
import io.appwin.core.push.AppwinPush
import io.appwin.core.push.AppwinPushHandler
import io.appwin.core.push.AppwinPushPayload

/** `appwin://community/post/<id>[/thread/<rootCommentId>]` (see `appwinPushRoutes` in the contract). */
internal fun communityPostTarget(deeplink: String?): AppwinCommunityPostTarget? {
  val route = deeplink?.let(AppwinPushPayload::routeOf) ?: return null
  if (route.product != "community") return null
  val segments = route.segments
  if (segments.size < 2 || segments[0] != "post" || segments[1].isBlank()) return null
  val thread = segments.getOrNull(3)?.takeIf { segments[2] == "thread" && it.isNotBlank() }
  return AppwinCommunityPostTarget(postId = segments[1], commentId = thread)
}

/**
 * Registered once by `initialize()` and kept for the process, so a tap reaches
 * Community even when no feed is on screen.
 */
internal class CommunityPushHandler(
  private val onTarget: (Context, AppwinCommunityPostTarget) -> Unit,
  private val onActivity: () -> Unit,
) : AppwinPushHandler {
  override fun onTap(context: Context, payload: AppwinPushPayload) {
    onActivity()
    communityPostTarget(payload.deeplink)?.let { onTarget(context, it) }
  }

  override fun onForeground(context: Context, payload: AppwinPushPayload): Boolean {
    onActivity()
    val target = communityPostTarget(payload.deeplink) ?: return false
    // Already reading that post: the detail is the notification.
    if (target.postId == OpenPost.postId) return true
    if (!AppwinInAppBanner.canPresent) return false
    val body = payload.body?.trim().orEmpty()
    if (body.isEmpty()) return false
    val strings = communityStrings(context)

    AppwinInAppBanner.present(
      AppwinBanner(
        id = "community:push:${target.postId}:${body.hashCode()}",
        title = payload.title?.trim().orEmpty().ifBlank { strings.title },
        body = body,
        onTap = { onTarget(context, target) },
      ),
    )
    return true
  }

  override fun onMessage(context: Context, payload: AppwinPushPayload): Boolean {
    onActivity()
    return false
  }
}

internal fun registerCommunityPush(handler: CommunityPushHandler) {
  AppwinPush.register("community", handler)
}

internal fun unregisterCommunityPush() {
  AppwinPush.unregister("community")
}
