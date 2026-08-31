package io.appwin.community

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    super.onCreate(savedInstanceState)
    setContent {
      CommunityRoot(onClose = { finish() })
    }
  }
}
