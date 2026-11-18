package io.appwin.support

import android.content.Context
import io.appwin.core.AppwinCore
import io.appwin.core.inapp.AppwinBanner
import io.appwin.core.inapp.AppwinInAppBanner
import io.appwin.support.data.SupportRepository
import io.appwin.support.domain.Conversation
import io.appwin.support.ui.MessengerConfigCache
import io.appwin.support.ui.OpenThread
import io.appwin.support.ui.SupportStrings
import io.appwin.support.ui.accentColorArgb
import io.appwin.support.ui.agentDisplayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Turns an inbound support reply into an in-app banner.
 *
 * The gap this closes: the server deliberately *skips* the push notification
 * when the customer has a live realtime connection - it assumes something
 * in-app will take over (`SupportCustomerPushService.notifyAgentReply`). Until
 * now nothing did, so a customer sitting in the app on any screen other than
 * the messenger was told nothing at all.
 *
 * Runs for the life of the process, not the life of a screen: `SupportViewModel`
 * also subscribes to these events, but only while the messenger is on screen,
 * which is precisely the case where a banner is *not* wanted.
 */
internal object SupportInAppWatcher {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val subscriptions = mutableListOf<UUID>()

  private var repository: SupportRepository? = null
  private var appContext: Context? = null
  private var started = false

  /**
   * Messages older than this are not announced.
   *
   * Set when the watcher starts, so opening the app does not replay every reply
   * received while it was closed - those already went out as push.
   */
  private var lastNotifiedMillis = 0L

  /**
   * Called by `initialize()`: no conversation means nobody can reply, so
   * there is nothing to listen for - the socket would be a per-user cost
   * with no upside. Until it opens, the server falls back to push.
   */
  fun startIfNeeded(context: Context, repository: SupportRepository) {
    if (!prefs(context).getBoolean(hasConversationsKey(), false)) return
    start(context, repository)
  }

  /**
   * A conversation was seen (non-empty list) or just created: from now on a
   * reply can arrive, so the watcher must be listening - this launch and
   * the next ones. No-op until the messenger context is known.
   */
  fun noteConversationsExist() {
    val context = AppwinSupport.context ?: return
    val prefs = prefs(context)
    if (!prefs.getBoolean(hasConversationsKey(), false)) {
      prefs.edit().putBoolean(hasConversationsKey(), true).apply()
    }
    start(context, AppwinSupport.watcherRepository)
  }

  private fun prefs(context: Context) =
    context.getSharedPreferences("appwin.support", Context.MODE_PRIVATE)

  private fun hasConversationsKey() = "hasConversations.${AppwinCore.projectAppId ?: ""}"

  fun start(context: Context, repository: SupportRepository) {
    if (started) return
    val hub = AppwinCore.realtimeHub() ?: return
    started = true
    this.repository = repository
    this.appContext = context.applicationContext
    lastNotifiedMillis = System.currentTimeMillis()

    // The same refetch on both: the events carry a minimal payload (a message
    // id and the customer), so the conversation list is what says who wrote and
    // what the preview is. `onConnected` covers a reply that landed while the
    // socket was down.
    subscriptions += hub.on("support.message.created") { check() }
    subscriptions += hub.onConnected { check() }
    hub.start()
  }

  fun stop() {
    val hub = AppwinCore.realtimeHub()
    subscriptions.forEach { hub?.off(it) }
    subscriptions.clear()
    started = false
  }

  private fun check() {
    val repo = repository ?: return
    scope.launch {
      val newest = runCatching { repo.conversations() }
        .getOrNull()
        ?.items
        ?.maxByOrNull { it.lastMessageAtMillis ?: it.createdAtMillis }
        ?: return@launch

      announceIfInbound(newest)
    }
  }

  private suspend fun announceIfInbound(conversation: Conversation) {
    val at = conversation.lastMessageAtMillis ?: return
    // The customer's own message raises the same event; only the studio's is
    // news to them.
    if (conversation.lastMessageAuthorType?.isStudio != true) return
    // Already reading that thread: the bubble is the notification.
    if (conversation.id == OpenThread.conversationId) return
    if (at <= lastNotifiedMillis) return
    lastNotifiedMillis = at

    val preview = conversation.preview?.trim().orEmpty()
    if (preview.isEmpty()) return

    val config = MessengerConfigCache.last
      ?: runCatching { repository?.config() }.getOrNull()?.also { MessengerConfigCache.last = it }
    val context = appContext ?: return

    AppwinInAppBanner.present(
      AppwinBanner(
        // Keyed by the message time, so a reconnect that replays the same
        // conversation does not stack a second banner.
        id = "support:${conversation.id}:$at",
        title = config?.agentDisplayName(context).orEmpty().ifBlank {
          SupportStrings(context).agentFallback
        },
        body = preview,
        accentArgb = config?.accentColorArgb,
        onTap = { AppwinSupport.presentConversation(context, conversation.id) },
      ),
    )
  }
}
