package io.appwin.notifications

import android.app.Application
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import io.appwin.core.AppForeground
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app presentation and the FCM token after [AppwinNotifications.start].
 */
@OptIn(AppwinInternalApi::class)
internal object NotificationsCoordinator {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var started = false
  private var application: Application? = null
  private var deferredRefetch: Job? = null
  private var lifecycleJobs: List<Job> = emptyList()

  /** Covers the async-automation race after an open; one shot, not a poll. */
  private const val DEFERRED_REFETCH_DELAY_MS = 5_000L
  private const val TAG = "AppwinNotifications"

  fun start(application: Application) {
    if (!AppwinNotifications.isReady || started) return
    started = true
    this.application = application
    InAppMessagePresenter.init(application)
    // Subscribed before the sync below, undispatched: start() syncs for the
    // launch itself, and AppForeground does not replay the launch to a
    // subscriber, so a cold launch syncs once.
    lifecycleJobs = listOf(
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        AppForeground.foreground.collect { onForeground() }
      },
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        AppForeground.background.collect { onBackground() }
      },
    )
    // The launch foreground is still to come (start() ran before the process
    // started, e.g. from Application.onCreate): it does the sync and the
    // deferred refetch, start() must not do them a second time.
    val launchPending = AppForeground.launchPending
    if (!launchPending) scope.launch { fetchAndPresent() }
    if (!launchPending) scheduleDeferredRefetch()
    // onNewToken alone misses the common case where FCM already minted a
    // token before the service was ready - pull the current one at start.
    scope.launch { registerCurrentFcmToken() }
  }

  fun stop() {
    lifecycleJobs.forEach { it.cancel() }
    lifecycleJobs = emptyList()
    application = null
    deferredRefetch?.cancel()
    deferredRefetch = null
    started = false
  }

  /** Fetches the pending messages and presents them. */
  suspend fun fetchAndPresent() {
    if (!AppwinNotifications.isReady) return
    runCatching {
      val messages = AppwinNotifications.fetchPendingMessages()
      InAppMessagePresenter.enqueue(messages)
    }
  }

  private fun onForeground() {
    scope.launch { fetchAndPresent() }
    scheduleDeferredRefetch()
  }

  private fun onBackground() {
    deferredRefetch?.cancel()
    deferredRefetch = null
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
