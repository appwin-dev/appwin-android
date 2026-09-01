package io.appwin.notifications

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Presents in-app messages one at a time over the host app. */
internal object InAppMessagePresenter {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var application: Application
  private val queue = ArrayDeque<InAppMessage>()
  private var isPresenting = false

  fun init(app: Application) {
    application = app
  }

  fun enqueue(messages: List<InAppMessage>) {
    if (!::application.isInitialized) return
    val unseen = messages.filter { message -> queue.none { it.deliveryId == message.deliveryId } }
    if (unseen.isEmpty()) return
    queue.addAll(unseen)
    presentNextIfNeeded()
  }

  private fun presentNextIfNeeded() {
    if (isPresenting || queue.isEmpty()) return
    isPresenting = true
    val message = queue.removeFirst()
    scope.launch {
      runCatching { AppwinNotifications.track(message.deliveryId, TrackEvent.OPENED) }
      val intent = Intent(application, InAppMessageActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(InAppMessageActivity.EXTRA_MESSAGE, InAppMessageActivity.encode(message))
      }
      application.startActivity(intent)
    }
  }

  fun onMessageClosed() {
    isPresenting = false
    presentNextIfNeeded()
  }
}
