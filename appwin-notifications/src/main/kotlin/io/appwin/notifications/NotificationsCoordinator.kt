package io.appwin.notifications

import android.app.Application
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.appwin.core.AppwinCore
import io.appwin.core.network.RealtimeHub
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lifecycle hooks, realtime delivery and in-app presentation after [AppwinNotifications.start].
 */
internal object NotificationsCoordinator : DefaultLifecycleObserver {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var started = false
  private var application: Application? = null
  private val realtimeSubIds = mutableListOf<UUID>()
  private var pushIntentCallbacks: ActivityLifecycleCallbacks? = null

  fun start(application: Application) {
    if (!AppwinNotifications.isReady || started) return
    started = true
    this.application = application
    InAppMessagePresenter.init(application)
    installPushIntentHandling(application)
    ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    installRealtime()
    scope.launch {
      AppwinNotifications.trackEvent(AutomationEvent.SESSION_START)
      refreshAndPresent()
    }
  }

  fun stop() {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    pushIntentCallbacks?.let { callbacks ->
      application?.unregisterActivityLifecycleCallbacks(callbacks)
    }
    pushIntentCallbacks = null
    application = null
    AppwinCore.realtimeHub()?.let { hub ->
      realtimeSubIds.forEach { hub.off(it) }
    }
    realtimeSubIds.clear()
    started = false
  }

  suspend fun refreshAndPresent() {
    if (!AppwinNotifications.isReady) return
    runCatching {
      val messages = AppwinNotifications.syncOnAppOpen()
      InAppMessagePresenter.enqueue(messages)
    }
  }

  override fun onStart(owner: LifecycleOwner) {
    scope.launch { refreshAndPresent() }
  }

  override fun onStop(owner: LifecycleOwner) {
    scope.launch { AppwinNotifications.trackEvent(AutomationEvent.APP_BACKGROUND) }
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

  private fun installRealtime() {
    val hub: RealtimeHub = AppwinCore.realtimeHub() ?: return
    realtimeSubIds += hub.on("notifications.message.pending") {
      scope.launch { refreshAndPresent() }
    }
    realtimeSubIds += hub.onConnected {
      scope.launch { refreshAndPresent() }
    }
    hub.start()
  }
}
