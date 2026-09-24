package io.appwin.notifications

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import io.appwin.core.AppwinCore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle hooks and in-app presentation after [AppwinNotifications.start].
 */
internal object NotificationsCoordinator : DefaultLifecycleObserver {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var started = false
  private var application: Application? = null
  private var deferredRefetch: Job? = null

  /** Covers the async-automation race after an open; one shot, not a poll. */
  private const val DEFERRED_REFETCH_DELAY_MS = 5_000L
  private const val TAG = "AppwinNotifications"

  fun start(application: Application) {
    if (!AppwinNotifications.isReady || started) return
    started = true
    this.application = application
    InAppMessagePresenter.init(application)
    ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    scope.launch {
      AppwinNotifications.trackEvent(AutomationEvent.SESSION_START)
      syncAndPresent()
    }
    // onNewToken alone misses the common case where FCM already minted a
    // token before the service was ready - pull the current one at start.
    scope.launch { registerCurrentFcmToken() }
    scheduleDeferredRefetch()
  }

  fun stop() {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    application = null
    deferredRefetch?.cancel()
    deferredRefetch = null
    started = false
  }

  /** Tracks `app_open` then presents pending messages (lifecycle / foreground). */
  suspend fun syncAndPresent() {
    if (!AppwinNotifications.isReady) return
    runCatching {
      val messages = AppwinNotifications.syncOnAppOpen()
      InAppMessagePresenter.enqueue(messages)
    }
  }

  /** Fetches pending messages without tracking `app_open` (deferred refetch). */
  suspend fun fetchAndPresent() {
    if (!AppwinNotifications.isReady) return
    runCatching {
      val messages = AppwinNotifications.fetchPendingMessages()
      InAppMessagePresenter.enqueue(messages)
    }
  }

  override fun onStart(owner: LifecycleOwner) {
    scope.launch {
      if (InAppMessagePresenter.consumeSuppressNextAppOpen()) {
        fetchAndPresent()
      } else {
        syncAndPresent()
      }
    }
    scheduleDeferredRefetch()
  }

  override fun onStop(owner: LifecycleOwner) {
    deferredRefetch?.cancel()
    deferredRefetch = null
    scope.launch { AppwinNotifications.trackEvent(AutomationEvent.APP_BACKGROUND) }
  }

  /**
   * No realtime socket: pending in-app messages are fetched on every
   * foreground, plus this one deferred refetch covering the only case a
   * socket was buying - an automation evaluated asynchronously just after
   * `app_open`, whose message lands seconds after the first fetch. A
   * campaign launched mid-session is delivered at the next open (or by
   * push, its own channel).
   */
  private fun scheduleDeferredRefetch() {
    deferredRefetch?.cancel()
    deferredRefetch = scope.launch {
      delay(DEFERRED_REFETCH_DELAY_MS)
      fetchAndPresent()
    }
  }

  /**
   * Host apps ship `firebase-messaging` + `google-services.json`; without them
   * this is a no-op. [AppwinFirebaseMessagingService.onNewToken] still covers
   * rotations.
   *
   * Suspend so Flutter / hosts that await [AppwinNotifications.awaitPushToken]
   * know when registration finished (or failed).
   */
  internal suspend fun registerCurrentFcmToken(): Boolean {
    return withContext(Dispatchers.IO) {
      runCatching {
        val token = Tasks.await(
          FirebaseMessaging.getInstance().token,
          15,
          TimeUnit.SECONDS,
        )
        if (token.isBlank()) {
          Log.w(TAG, "FCM token empty after await")
          return@runCatching false
        }
        AppwinCore.registerPushToken(token)
        Log.i(TAG, "FCM token registered (${token.take(12)}…)")
        true
      }.getOrElse { err ->
        Log.e(TAG, "FCM token registration failed", err)
        false
      }
    }
  }
}
