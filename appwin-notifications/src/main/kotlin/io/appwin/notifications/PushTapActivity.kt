package io.appwin.notifications

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.appwin.core.AppwinInternalApi
import io.appwin.core.push.AppwinPush
import io.appwin.core.push.AppwinPushPayload

/** Trampoline for taps on notifications the SDK posted itself (app in foreground). */
@OptIn(AppwinInternalApi::class)
public class PushTapActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    route(intent)
    finish()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    route(intent)
    finish()
  }

  private fun route(intent: Intent?) {
    val deeplink = intent?.getStringExtra(AppwinPushPayload.KEY_DEEPLINK)
    val external = deeplink != null && AppwinPushPayload.routeOf(deeplink) == null
    // Brings the host up first: the product that opens an internal route
    // (the Support messenger) draws over it, and on a cold start it is the
    // host that initializes that product and replays the kept tap. An external
    // link goes to its own app; bringing ours up behind it helps nobody.
    if (!external) bringHostToFront()
    if (!AppwinPush.handleTap(this, intent) && deeplink != null) {
      AppwinPush.openDeeplink(this, deeplink)
    }
  }

  private fun bringHostToFront() {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
    // Same flags as the launcher icon: resumes the existing task as it was
    // instead of stacking a fresh launcher activity on it.
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { startActivity(launch) }
  }
}
