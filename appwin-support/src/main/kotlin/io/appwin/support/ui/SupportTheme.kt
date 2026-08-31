package io.appwin.support.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
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
 */
@Composable
internal fun SupportTheme(config: MessengerConfig, content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  val primary = parseHexColor(config.branding.primaryHex) ?: DefaultPrimary
  val onPrimary = parseHexColor(config.branding.primaryForegroundHex)
    ?: contrastingForeground(primary)

  val colors = if (dark) {
    darkColorScheme(
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
      background = Color(0xFF0B0F16),
      surface = Color(0xFF121821),
      surfaceVariant = Color(0xFF1B222D),
      onSurfaceVariant = Color(0xFF9AA7BD),
      outlineVariant = Color(0xFF232B38),
    )
  } else {
    lightColorScheme(
      primary = primary,
      onPrimary = onPrimary,
      primaryContainer = primary,
      onPrimaryContainer = onPrimary,
      secondaryContainer = primary,
      onSecondaryContainer = onPrimary,
      surfaceTint = primary,
      background = Color(0xFFF8FAFC),
      surface = Color.White,
      surfaceVariant = Color(0xFFF1F5F9),
      onSurfaceVariant = Color(0xFF64748B),
      outlineVariant = Color(0xFFE2E8F0),
    )
  }

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

/**
 * Black or white, depending on the accent's luminance.
 *
 * A fallback for when the studio has not set the foreground colour: white text
 * on a yellow accent is unreadable, and that is the kind of detail you only see
 * in production.
 */
internal fun contrastingForeground(background: Color): Color {
  fun linear(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
  }
  val luminance =
    0.2126 * linear(background.red) + 0.7152 * linear(background.green) + 0.0722 * linear(background.blue)
  return if (luminance > 0.5) Color.Black else Color.White
}

/**
 * `#RGB`, `#RRGGBB` or `#AARRGGBB` to a colour, `null` when unreadable.
 *
 * The caller then falls back to the default accent: a colour mistyped in the
 * studio must not make the buttons transparent.
 */
internal fun parseHexColor(raw: String?): Color? {
  val hex = raw?.trim()?.removePrefix("#") ?: return null
  val normalized = when (hex.length) {
    3 -> hex.map { "$it$it" }.joinToString("")
    6, 8 -> hex
    else -> return null
  }
  val value = normalized.toLongOrNull(16) ?: return null
  return if (normalized.length == 6) Color(value or 0xFF000000L) else Color(value)
}

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
  val messagePlaceholder: String
    get() = t("appwin_support_message_placeholder", "Write a message…")
  val loadErrorTitle: String get() = t("appwin_support_load_error_title", "Couldn't load")
  val loadErrorMessage: String
    get() = t("appwin_support_load_error_message", "Check your connection and try again.")
  val statusResolved: String get() = t("appwin_support_status_resolved", "Resolved")
  val statusClosed: String get() = t("appwin_support_status_closed", "Closed")
  val seen: String get() = t("appwin_support_seen", "Seen")
}
