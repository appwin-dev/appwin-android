package io.appwin.community

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appwin.community.ui.CommunityRoot
import io.appwin.community.ui.CommunityViewModel
import io.appwin.community.ui.HostProfileEditing
import io.appwin.community.ui.LocalProfileEditing
import io.appwin.community.ui.ProfileEditing
import io.appwin.community.ui.isCommunityDark

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
    applySystemBars(dark = false)
    super.onCreate(savedInstanceState)
    val target = intent.getStringExtra(EXTRA_POST_ID)?.let {
      AppwinCommunityPostTarget(postId = it, commentId = intent.getStringExtra(EXTRA_COMMENT_ID))
    }
    val presentation = intent.getLongExtra(EXTRA_PRESENTATION, NO_PRESENTATION)
    val profileEditing = ProfileEditing(
      handlers = HostProfileEditing.presentation(presentation),
      stepAside = { finish() },
    )
    setContent {
      // Same activity-scoped instance CommunityRoot picks up, so the bar icons
      // follow the scheme the studio configured once the config is in.
      val viewModel: CommunityViewModel = viewModel()
      val state by viewModel.state.collectAsStateWithLifecycle()
      val dark = isCommunityDark(state.config)
      LaunchedEffect(dark) { applySystemBars(dark) }
      CompositionLocalProvider(LocalProfileEditing provides profileEditing) {
        CommunityRoot(viewModel = viewModel, onClose = { finish() }, initialTarget = target)
      }
    }
  }

  override fun onDestroy() {
    if (isFinishing) {
      intent.getLongExtra(EXTRA_PRESENTATION, NO_PRESENTATION)
        .takeIf { it != NO_PRESENTATION }
        ?.let(HostProfileEditing::endPresentation)
    }
    super.onDestroy()
  }

  private fun applySystemBars(dark: Boolean) {
    val style =
      if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
      else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
  }

  internal companion object {
    private const val EXTRA_POST_ID = "io.appwin.community.POST_ID"
    private const val EXTRA_COMMENT_ID = "io.appwin.community.COMMENT_ID"
    private const val EXTRA_PRESENTATION = "io.appwin.community.PRESENTATION"
    private const val NO_PRESENTATION = 0L

    fun presentIntent(context: Context, presentation: Long): Intent =
      Intent(context, CommunityActivity::class.java).putExtra(EXTRA_PRESENTATION, presentation)

    fun postIntent(context: Context, target: AppwinCommunityPostTarget): Intent =
      Intent(context, CommunityActivity::class.java)
        .putExtra(EXTRA_POST_ID, target.postId)
        .putExtra(EXTRA_COMMENT_ID, target.commentId)
  }
}
