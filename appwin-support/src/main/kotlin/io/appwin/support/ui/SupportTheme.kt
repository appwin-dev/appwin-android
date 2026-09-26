package io.appwin.support.ui

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.appwin.support.R
import io.appwin.support.domain.MessengerConfig

/**
 * Messenger theme, driven by the studio's configuration.
 *
 * Accent colour and corner radius come from the dashboard; the SDK re-reads the
 * configuration on every open, so a change applies without republishing the app.
 *
 * Light only, like the iOS SDK: the palette is a fixed set of slate tones drawn
 * against white, and following the host app into dark mode turns the sheet into
 * unreadable bands.
 */
@Composable
internal fun SupportTheme(config: MessengerConfig, content: @Composable () -> Unit) {
  val primary = config.accentColor
  val onPrimary = config.onAccentColor

  val colors = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    // See CommunityTheme: Material 3 leaves the containers on its default
    // purple, and those are what the chips and floating buttons use. The
    // studio's accent must apply there too.
    primaryContainer = primary,
    onPrimaryContainer = onPrimary,
    secondaryContainer = primary,
    onSecondaryContainer = onPrimary,
    surfaceTint = primary,
    background = SupportTokens.sheetBackground,
    surface = SupportTokens.surface,
    surfaceVariant = SupportTokens.sheetBackground,
    onSurface = SupportTokens.textMain,
    onSurfaceVariant = SupportTokens.textSecondary,
    outlineVariant = SupportTokens.border,
  )

  val radius = config.design.radius.dp.dp

  MaterialTheme(
    colorScheme = colors,
    shapes = Shapes(
      small = RoundedCornerShape(radius / 2),
      medium = RoundedCornerShape(radius),
      large = RoundedCornerShape(radius),
    ),
    content = content,
  )
}

private val DefaultPrimary = Color(0xFFF97316)

/** Studio accent, falling back to the SDK default when unreadable. */
internal val MessengerConfig.accentColor: Color
  get() = parseHexColor(branding.primaryHex) ?: DefaultPrimary

/** Foreground on the accent, computed when the studio has not set one. */
internal val MessengerConfig.onAccentColor: Color
  get() = parseHexColor(branding.primaryForegroundHex) ?: contrastingForeground(accentColor)

/**
 * The accent as a plain ARGB int, for surfaces drawn outside Compose.
 *
 * The in-app banner lives in Core and knows nothing of `Color`; it takes the
 * packed int the framework uses.
 */
internal val MessengerConfig.accentColorArgb: Int
  get() = accentColor.toArgb()

/**
 * Agent name for a notification, resolved without a composition.
 *
 * Same fallback chain as the sheet header / iOS `Header`: custom agent label,
 * then the resolved context agent, then the project name.
 */
internal fun MessengerConfig.agentDisplayName(context: Context): String {
  val strings = SupportStrings(context)
  return headerTitle(strings)
}

/**
 * Sheet title - same order as the dashboard Personnaliser preview and iOS.
 *
 * Prefer the agent label (`Support {project}` when the studio left it blank),
 * then the project name, then the generic SDK label. Never show only the raw
 * project slug when an agent name is available.
 */
internal fun MessengerConfig.headerTitle(strings: SupportStrings): String {
  val agent = messaging.agentName?.trim().orEmpty().ifBlank { context.agentName.trim() }
  if (agent.isNotEmpty()) return agent
  return context.projectName.trim().ifBlank { strings.title }
}

/**
 * Messenger labels, resolved from the **device locale**.
 *
 * The AAR ships `values` (English) and `values-fr` (French). Android picks the
 * right table from the phone language. A studio can still override any key by
 * defining the same `appwin_support_*` name in the host app - that wins.
 */
internal class SupportStrings(private val context: Context) {
  private fun t(key: String, resId: Int): String {
    val hostId = context.resources.getIdentifier(key, "string", context.packageName)
    if (hostId != 0) {
      return runCatching { context.getString(hostId) }.getOrElse { context.getString(resId) }
    }
    return context.getString(resId)
  }

  val title: String get() = t("appwin_support_title", R.string.appwin_support_title)
  val close: String get() = t("appwin_support_close", R.string.appwin_support_close)
  val retry: String get() = t("appwin_support_retry", R.string.appwin_support_retry)
  val send: String get() = t("appwin_support_send", R.string.appwin_support_send)
  val newConversation: String
    get() = t("appwin_support_new_conversation", R.string.appwin_support_new_conversation)
  val recentMessage: String
    get() = t("appwin_support_recent_message", R.string.appwin_support_recent_message)
  fun youPreview(body: String): String =
    context.getString(R.string.appwin_support_you_preview, body)
  val conversations: String
    get() = t("appwin_support_conversations", R.string.appwin_support_conversations)
  val conversationsTitle: String
    get() = t("appwin_support_conversations_title", R.string.appwin_support_conversations_title)
  val noConversation: String
    get() = t("appwin_support_no_conversation", R.string.appwin_support_no_conversation)
  val emptyConversationsHint: String
    get() = t(
      "appwin_support_empty_conversations_hint",
      R.string.appwin_support_empty_conversations_hint,
    )
  val writeMessage: String
    get() = t("appwin_support_write_message", R.string.appwin_support_write_message)
  val newConversationPreview: String
    get() = t(
      "appwin_support_new_conversation_preview",
      R.string.appwin_support_new_conversation_preview,
    )
  val mediaPhoto: String
    get() = t("appwin_support_media_photo", R.string.appwin_support_media_photo)
  val mediaVideo: String
    get() = t("appwin_support_media_video", R.string.appwin_support_media_video)
  val mediaPdf: String
    get() = t("appwin_support_media_pdf", R.string.appwin_support_media_pdf)
  val mediaFile: String
    get() = t("appwin_support_media_file", R.string.appwin_support_media_file)
  val yesterday: String
    get() = t("appwin_support_yesterday", R.string.appwin_support_yesterday)
  val today: String
    get() = t("appwin_support_today", R.string.appwin_support_today)
  val faq: String get() = t("appwin_support_faq", R.string.appwin_support_faq)

  /**
   * Two lines, as in the design. The customer's name is not part of the SDK's
   * home state, so the greeting stays generic unless the studio overrides it.
   */
  val greeting: String get() = t("appwin_support_greeting", R.string.appwin_support_greeting)

  val messagePlaceholder: String
    get() = t("appwin_support_message_placeholder", R.string.appwin_support_message_placeholder)
  val loadErrorTitle: String
    get() = t("appwin_support_load_error_title", R.string.appwin_support_load_error_title)
  val loadErrorMessage: String
    get() = t("appwin_support_load_error_message", R.string.appwin_support_load_error_message)
  val statusResolved: String
    get() = t("appwin_support_status_resolved", R.string.appwin_support_status_resolved)
  val statusClosed: String
    get() = t("appwin_support_status_closed", R.string.appwin_support_status_closed)
  val seen: String get() = t("appwin_support_seen", R.string.appwin_support_seen)
  val agentFallback: String
    get() = t("appwin_support_agent_fallback", R.string.appwin_support_agent_fallback)
  val attachImage: String
    get() = t("appwin_support_attach_image", R.string.appwin_support_attach_image)
  val attachVideo: String
    get() = t("appwin_support_attach_video", R.string.appwin_support_attach_video)
  val attachFile: String
    get() = t("appwin_support_attach_file", R.string.appwin_support_attach_file)
  val typing: String get() = t("appwin_support_typing", R.string.appwin_support_typing)
  val emoji: String get() = t("appwin_support_emoji", R.string.appwin_support_emoji)
  val showOriginal: String
    get() = t("appwin_support_show_original", R.string.appwin_support_show_original)
  val seeTranslation: String
    get() = t("appwin_support_see_translation", R.string.appwin_support_see_translation)
  val editMessage: String
    get() = t("appwin_support_edit_message", R.string.appwin_support_edit_message)
  val deleteMessage: String
    get() = t("appwin_support_delete_message", R.string.appwin_support_delete_message)
  val editingBanner: String
    get() = t("appwin_support_editing_banner", R.string.appwin_support_editing_banner)
  val cancel: String get() = t("appwin_support_cancel", R.string.appwin_support_cancel)
  val save: String get() = t("appwin_support_save", R.string.appwin_support_save)

  fun openFile(filename: String): String =
    context.getString(R.string.appwin_support_open_file, filename)

  fun relativeNow(): String = t("appwin_support_relative_now", R.string.appwin_support_relative_now)

  fun relativeMinutes(n: Int): String =
    context.getString(R.string.appwin_support_relative_minutes, n)

  fun relativeHours(n: Int): String =
    context.getString(R.string.appwin_support_relative_hours, n)

  fun relativeDays(n: Int): String =
    context.getString(R.string.appwin_support_relative_days, n)

  fun relativeWeeks(n: Int): String =
    context.getString(R.string.appwin_support_relative_weeks, n)
}
