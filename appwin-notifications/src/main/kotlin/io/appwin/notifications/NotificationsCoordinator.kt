package io.appwin.notifications

import android.app.Application
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lifecycle hooks and in-app presentation after [AppwinNotifications.start].
 */
internal object NotificationsCoordinator : DefaultLifecycleObserver {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var started = false
  private var application: Application? = null
  private var deferredRefetch: Job? = null
  private var pushIntentCallbacks: ActivityLifecycleCallbacks? = null

  /** Covers the async-automation race after an open; one shot, not a poll. */
  private const val DEFERRED_REFETCH_DELAY_MS = 5_000L

  fun start(application: Application) {
    if (!AppwinNotifications.isReady || started) return
    started = true
    this.application = application
    InAppMessagePresenter.init(application)
    installPushIntentHandling(application)
    ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    scope.launch {
      AppwinNotifications.trackEvent(AutomationEvent.SESSION_START)
      syncAndPresent()
    }
    scheduleDeferredRefetch()
  }

  fun stop() {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    pushIntentCallbacks?.let { callbacks ->
      application?.unregisterActivityLifecycleCallbacks(callbacks)
    }
    pushIntentCallbacks = null
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

  private fun installPushIntentHandling(application: Application) {
    val callbacks = object : ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityResumed(activity: Activity) {
        PushDeepLinkHandler.dispatch(activity, activity.intent)
      }
      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    }
    application.registerActivityLifecycleCallbacks(callbacks)
    pushIntentCallbacks = callbacks
  }

}
