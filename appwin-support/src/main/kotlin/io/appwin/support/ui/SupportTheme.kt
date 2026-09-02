package io.appwin.support.ui

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

/** Sheet title: the studio's project name, or the generic label. */
internal fun MessengerConfig.headerTitle(strings: SupportStrings): String =
  context.projectName.ifBlank { strings.title }

/**
 * Messenger labels, resolved from the **host app's** resources.
 *
 * Same mechanism as on the Community side and as iOS's `NSLocalizedString`: the
 * studio translates or rewrites a label by defining the key on its side, without
 * waiting for an SDK release. With no resource, the English default is shown.
 */
internal class SupportStrings(private val context: Context) {
  private fun t(key: String, fallback: String): String {
    val id = context.resources.getIdentifier(key, "string", context.packageName)
    return if (id == 0) fallback else runCatching { context.getString(id) }.getOrDefault(fallback)
  }

  val title: String get() = t("appwin_support_title", "Help")
  val close: String get() = t("appwin_support_close", "Close")
  val retry: String get() = t("appwin_support_retry", "Retry")
  val send: String get() = t("appwin_support_send", "Send")
  val newConversation: String get() = t("appwin_support_new_conversation", "Send us a message")
  val conversations: String get() = t("appwin_support_conversations", "Your conversations")
  val noConversation: String get() = t("appwin_support_no_conversation", "No conversation yet")
  val faq: String get() = t("appwin_support_faq", "Help centre")

  /**
   * Two lines, as in the design. The customer's name is not part of the SDK's
   * home state, so the greeting stays generic unless the studio overrides it.
   */
  val greeting: String get() = t("appwin_support_greeting", "Hello 👋\nNeed help?")

  val messagePlaceholder: String
    get() = t("appwin_support_message_placeholder", "Write a message…")
  val loadErrorTitle: String get() = t("appwin_support_load_error_title", "Couldn't load")
  val loadErrorMessage: String
    get() = t("appwin_support_load_error_message", "Check your connection and try again.")
  val statusResolved: String get() = t("appwin_support_status_resolved", "Resolved")
  val statusClosed: String get() = t("appwin_support_status_closed", "Closed")
  val seen: String get() = t("appwin_support_seen", "Seen")
}
