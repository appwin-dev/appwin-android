package io.appwin.support

import android.content.Context
import io.appwin.core.AppwinInternalApi
import io.appwin.core.inapp.AppwinBanner
import io.appwin.core.inapp.AppwinInAppBanner
import io.appwin.core.push.AppwinPushHandler
import io.appwin.core.push.AppwinPushPayload
import io.appwin.support.ui.MessengerConfigCache
import io.appwin.support.ui.OpenThread
import io.appwin.support.ui.SupportStrings
import io.appwin.support.ui.accentColorArgb
import io.appwin.support.ui.agentDisplayName

/** Support's share of a push: a reply from the studio. */
@OptIn(AppwinInternalApi::class)
internal object SupportPushHandler : AppwinPushHandler {

  override fun onTap(context: Context, payload: AppwinPushPayload) {
    val conversationId = conversationId(payload.deeplink)
    if (conversationId != null) {
      AppwinSupport.presentConversation(context, conversationId)
    } else {
      AppwinSupport.presentMessenger(context)
    }
  }

  override fun onForeground(context: Context, payload: AppwinPushPayload): Boolean {
    val conversationId = conversationId(payload.deeplink) ?: return false
    // Already reading that thread: the new bubble is the notification.
    if (conversationId == OpenThread.conversationId) return true
    if (!AppwinInAppBanner.canPresent) return false

    val config = MessengerConfigCache.last
    AppwinInAppBanner.present(
      AppwinBanner(
        // Two replies in a row are two banners; the same push seen twice is one.
        id = "support:push:$conversationId:${payload.body.hashCode()}",
        title = payload.title
          ?: config?.agentDisplayName(context)?.ifBlank { null }
          ?: SupportStrings(context).agentFallback,
        body = payload.body.orEmpty(),
        accentArgb = config?.accentColorArgb,
        onTap = { AppwinSupport.presentConversation(context, conversationId) },
      ),
    )
    return true
  }

  /** `appwin://support/conversation/<id>` (see `appwinPushRoutes` in the contract). */
  internal fun conversationId(deeplink: String?): String? {
    val route = deeplink?.let(AppwinPushPayload::routeOf) ?: return null
    if (route.product != "support") return null
    val segments = route.segments
    if (segments.size < 2 || segments[0] != "conversation") return null
    return segments[1].takeIf { it.isNotBlank() }
  }
}
