package io.appwin.notifications

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Trampoline for notification content taps (deeplink + optional delivery tracking). */
public class PushTapActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    PushDeepLinkHandler.dispatch(this, intent)
    finish()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    PushDeepLinkHandler.dispatch(this, intent)
    finish()
  }
}
