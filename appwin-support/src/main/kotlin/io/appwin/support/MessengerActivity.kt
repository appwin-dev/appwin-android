package io.appwin.support

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.appwin.support.ui.MessengerSheet

/**
 * Messenger presented as a sheet over the host app, opened by
 * [AppwinSupport.presentMessenger].
 *
 * The window itself is translucent (see `Theme.Appwin.Sdk.Sheet`): what the user
 * sees as the sheet is drawn by Compose, and the host app stays visible behind
 * it. Dismissing plays the sheet's own animation, then finishes without an
 * activity transition - two overlapping animations read as a glitch.
 *
 * The embedded mode goes through [AppwinSupport.MessengerView] and has no sheet
 * and no close button: there is already a tab to leave by.
 */
public class MessengerActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MessengerSheet(onDismissed = {
        finish()
        overridePendingTransition(0, 0)
      })
    }
  }
}
