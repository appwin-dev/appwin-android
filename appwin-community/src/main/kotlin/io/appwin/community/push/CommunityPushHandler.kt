@file:OptIn(AppwinInternalApi::class)

package io.appwin.community.push

import android.content.Context
import io.appwin.core.AppwinInternalApi
import io.appwin.core.push.AppwinPush
import io.appwin.core.push.AppwinPushHandler
import io.appwin.core.push.AppwinPushPayload

/** Community push target: a post, optionally the replies thread under one comment. */
internal data class CommunityPushTarget(val postId: String, val threadCommentId: String? = null) {
  internal companion object {
    /** `appwin://community/post/<id>[/thread/<rootCommentId>]` (see `appwinPushRoutes` in the contract). */
    fun from(deeplink: String?): CommunityPushTarget? {
      val route = deeplink?.let(AppwinPushPayload::routeOf) ?: return null
      if (route.product != "community") return null
      val segments = route.segments
      if (segments.size < 2 || segments[0] != "post" || segments[1].isBlank()) return null
      val thread = segments.getOrNull(3)?.takeIf { segments[2] == "thread" && it.isNotBlank() }
      return CommunityPushTarget(postId = segments[1], threadCommentId = thread)
    }
  }
}

internal class CommunityPushHandler(
  private val open: (CommunityPushTarget) -> Unit,
) : AppwinPushHandler {
  override fun onTap(context: Context, payload: AppwinPushPayload) {
    CommunityPushTarget.from(payload.deeplink)?.let(open)
  }
}

/**
 * Registered only while the feed is composed: opening a post needs its
 * navigation state, so AppwinPush keeps a tap until the feed mounts.
 */
internal fun registerCommunityPush(open: (CommunityPushTarget) -> Unit) {
  AppwinPush.register("community", CommunityPushHandler(open))
}

internal fun unregisterCommunityPush() {
  AppwinPush.unregister("community")
}
