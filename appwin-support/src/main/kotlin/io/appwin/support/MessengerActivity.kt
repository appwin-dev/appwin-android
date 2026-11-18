package io.appwin.support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.appwin.core.availability.AppwinPause
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
    val conversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
    val unavailable = intent?.getBooleanExtra(EXTRA_UNAVAILABLE, false) == true
    // From the intent only: the singleton is empty after a process death, and a pause that
    // ended since the tap must not turn this sheet into the live messenger.
    val pause = intent?.let(::pauseFromExtras)
    setContent {
      MessengerSheet(
        initialConversationId = conversationId,
        unavailable = unavailable,
        pause = pause,
        onDismissed = {
          finish()
          overridePendingTransition(0, 0)
        },
      )
    }
  }

  public companion object {
    /** Opens straight onto that thread; absent means the home screen. */
    public const val EXTRA_CONVERSATION_ID: String = "appwin.support.conversationId"

    /** Debuggable hosts: the sheet carries the diagnosis instead of the messenger. */
    internal const val EXTRA_UNAVAILABLE: String = "appwin.support.unavailable"

    /** The organisation is on pause: the sheet carries the "paused" screen instead of the messenger. */
    internal const val EXTRA_PAUSE_SINCE: String = "appwin.support.pause.since"
    internal const val EXTRA_PAUSE_TITLE: String = "appwin.support.pause.title"
    internal const val EXTRA_PAUSE_MESSAGE: String = "appwin.support.pause.message"

    internal fun pausedIntent(context: Context, pause: AppwinPause): Intent =
      Intent(context, MessengerActivity::class.java)
        .putExtra(EXTRA_PAUSE_SINCE, pause.since)
        .putExtra(EXTRA_PAUSE_TITLE, pause.title)
        .putExtra(EXTRA_PAUSE_MESSAGE, pause.message)
  }
}

/** The pause a [MessengerActivity] was opened for; null unless `since` is there. */
internal fun pauseFromExtras(intent: Intent): AppwinPause? {
  val since = intent.getStringExtra(MessengerActivity.EXTRA_PAUSE_SINCE) ?: return null
  return AppwinPause(
    since = since,
    title = intent.getStringExtra(MessengerActivity.EXTRA_PAUSE_TITLE).orEmpty(),
    message = intent.getStringExtra(MessengerActivity.EXTRA_PAUSE_MESSAGE).orEmpty(),
  )
}
