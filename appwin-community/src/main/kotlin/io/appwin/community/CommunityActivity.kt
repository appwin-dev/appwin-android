package io.appwin.community

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
 * tab in the host app. The embedded mode goes through
 * [AppwinCommunity.CommunityView] and has no close button.
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
    setContent {
      CommunityRoot(onClose = { finish() })
    }
  }
}
