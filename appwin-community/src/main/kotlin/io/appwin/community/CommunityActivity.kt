package io.appwin.community

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.appwin.community.ui.CommunityRoot

/**
 * Community feed, full screen.
 *
 * Used by [AppwinCommunity.presentCommunity] when the community has no dedicated
 * tab in the host app, and to open a post from a notification when no feed is
 * on screen. The embedded mode goes through [AppwinCommunity.CommunityView] and
 * has no close button.
 */
public class CommunityActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // Edge-to-edge so Compose WindowInsets match the cutout / gesture bar;
    // CommunityRoot pads chrome (header, composers) itself.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    super.onCreate(savedInstanceState)
    val target = intent.getStringExtra(EXTRA_POST_ID)?.let {
      AppwinCommunityPostTarget(postId = it, commentId = intent.getStringExtra(EXTRA_COMMENT_ID))
    }
    setContent {
      CommunityRoot(onClose = { finish() }, initialTarget = target)
    }
  }

  internal companion object {
    private const val EXTRA_POST_ID = "io.appwin.community.POST_ID"
    private const val EXTRA_COMMENT_ID = "io.appwin.community.COMMENT_ID"

    fun postIntent(context: Context, target: AppwinCommunityPostTarget): Intent =
      Intent(context, CommunityActivity::class.java)
        .putExtra(EXTRA_POST_ID, target.postId)
        .putExtra(EXTRA_COMMENT_ID, target.commentId)
  }
}
