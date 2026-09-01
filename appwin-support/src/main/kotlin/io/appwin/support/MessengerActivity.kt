package io.appwin.support

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.appwin.support.ui.MessengerRoot

/**
 * Messenger, full screen, opened by [AppwinSupport.presentMessenger].
 *
 * The embedded mode goes through [AppwinSupport.MessengerView] and has no close
 * button: there is already a tab to leave by.
 */
public class MessengerActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MessengerRoot(onClose = { finish() })
    }
  }
}
