package io.appwin.community.push

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.appwin.community.AppwinCommunityPostTarget
import io.appwin.community.CommunityActivity

/** Opens a post target where the member will see it. */
internal object CommunityOpener {
  private val main = Handler(Looper.getMainLooper())
  private const val FEED_GRACE_MS = 600L

  /**
   * Always posted, never run inline: AppwinPush replays a cold-start tap
   * synchronously inside `initialize()`, before a host that sets
   * `onNotificationTap` right after it has had the chance to.
   */
  fun open(
    context: Context,
    target: AppwinCommunityPostTarget,
    fromTap: Boolean,
    hostTapHandler: () -> ((AppwinCommunityPostTarget) -> Unit)?,
  ) {
    main.post {
      val host = hostTapHandler()
      val visible = CommunityFeedPresence.visible()
      val mounted = CommunityFeedPresence.mounted()
      when (communityOpenRoute(fromTap, host != null, visible != null, mounted != null)) {
        CommunityOpenRoute.HOST -> host?.invoke(target)
        CommunityOpenRoute.FEED -> (visible ?: mounted)?.open?.invoke(target)
        CommunityOpenRoute.AWAIT_FEED ->
          CommunityFeedPresence.awaitFeed(target, FEED_GRACE_MS) { presentModal(context, target) }
        CommunityOpenRoute.MODAL -> presentModal(context, target)
      }
    }
  }

  private fun presentModal(context: Context, target: AppwinCommunityPostTarget) {
    val intent = CommunityActivity.postIntent(context, target)
    // A push tap hands the application context, which can only start an
    // activity in a new task; the app's own affinity puts it over the host.
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }
}
