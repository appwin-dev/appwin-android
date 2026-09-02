package io.appwin.support.domain

/**
 * Support domain entities.
 *
 * Mirror of the Swift entities: same names, same fields. Network DTOs live
 * apart and never cross this boundary.
 */

public enum class ConversationStatus(public val wire: String) {
  OPEN("open"),
  RESOLVED("resolved"),
  CLOSED("closed"),
  ;

  public companion object {
    public fun from(wire: String?): ConversationStatus =
      entries.firstOrNull { it.wire == wire } ?: OPEN
  }
}

public enum class MessageAuthorType(public val wire: String) {
  CUSTOMER("customer"),

  /** Studio member. The wire value is `organization_member`. */
  ORGANIZATION_MEMBER("organization_member"),

  /** Autonomous AI reply: a studio-side bubble, like a human's. */
  AI_ASSISTANT("ai_assistant"),
  ;

  /** Vrai si le message vient du studio, humain ou IA. */
  public val isStudio: Boolean
    get() = this != CUSTOMER

  public companion object {
    public fun from(wire: String?): MessageAuthorType =
      entries.firstOrNull { it.wire == wire } ?: CUSTOMER
  }
}

public data class Conversation(
  public val id: String,
  /** Last message preview, for the list. */
  public val preview: String?,
  public val status: ConversationStatus,
  /** Last message date, which sorts the list. */
  public val lastMessageAtMillis: Long?,
  /** Last read by the customer, which drives the unread badge. */
  public val lastReadAtMillis: Long?,
  public val createdAtMillis: Long,
) {
  /**
   * True when a message arrived after the last read.
   *
   * Computed client-side rather than received from the server: reading is a
   * local state that moves on every screen open, and the round trip would make
   * clignoter la pastille.
   */
  public val hasUnread: Boolean
    get() {
      val last = lastMessageAtMillis ?: return false
      val read = lastReadAtMillis ?: return true
      return last > read
    }
}

public data class MessageReaction(
  public val emoji: String,
  public val count: Int,
  public val reactedByMe: Boolean,
)

public data class Attachment(
  public val id: String,
  public val messageId: String,
  public val filename: String,
  public val mimeType: String,
  public val sizeBytes: Int,
  public val url: String,
) {
  public val isImage: Boolean get() = mimeType.startsWith("image/")
}

public data class Message(
  public val id: String,
  /** Drives the bubble's alignment and colour. */
  public val authorType: MessageAuthorType,
  /** Display name for studio messages. */
  public val authorName: String?,
  public val body: String,
  public val readAtMillis: Long?,
  public val createdAtMillis: Long,
  public val attachments: List<Attachment> = emptyList(),
  public val reactions: List<MessageReaction> = emptyList(),
)

/** Quick reactions offered under a bubble, aligned with iOS. */
public object QuickMessageReactions {
  public val all: List<String> = listOf("👍", "🔥", "❤️", "😂", "😮", "🎉")
}

public data class FaqCategory(public val id: String, public val name: String)

public data class Faq(
  public val id: String,
  public val categoryId: String,
  public val question: String,
  public val answer: String,
  public val position: Int = 0,
)

/** A category and its articles, ready to display. */
public data class FaqGroup(
  public val category: FaqCategory,
  public val articles: List<Faq>,
)

/* -------------------------------------------------------------------------- */
/* Configuration du messenger                                                 */
/* -------------------------------------------------------------------------- */

public enum class MessengerRadius(public val wire: String, public val dp: Int) {
  LOW("low", 8),
  MEDIUM("medium", 12),
  HIGH("high", 16),
  MAX("max", 24),
  ;

  public companion object {
    public fun from(wire: String?): MessengerRadius =
      entries.firstOrNull { it.wire == wire } ?: HIGH
  }
}

/** Where the home banner art comes from. */
public enum class MessengerBannerSource(public val wire: String) {
  PRESET("preset"),
  CUSTOM("custom"),
  NONE("none"),
  ;

  public companion object {
    public fun from(wire: String?): MessengerBannerSource =
      entries.firstOrNull { it.wire == wire } ?: PRESET
  }
}

/**
 * Banner art bundled with the SDK, mirroring the dashboard presets.
 *
 * Rasterised from the same sources as the iOS bundle, so a studio switching
 * preset sees the same picture on both platforms.
 */
public enum class MessengerPresetBanner(public val wire: String) {
  EMOJIS("emojis"),
  AMICALE("amicale"),
  DISCRET("discret"),
  PHOTO("photo"),
  ICON("icon"),
  SERIOUS("serious"),
  ;

  public companion object {
    public fun from(wire: String?): MessengerPresetBanner =
      entries.firstOrNull { it.wire == wire } ?: EMOJIS
  }
}

public data class MessengerBranding(
  public val primaryHex: String = "#F97316",
  public val primaryForegroundHex: String = "#FFFFFF",
)

public data class MessengerModules(public val faqEnabled: Boolean = true)

public data class MessengerMessaging(
  public val agentName: String? = null,
  public val agentAvatarUrl: String? = null,
  public val welcomeMessage: String? = null,
  public val welcomeMessageEnabled: Boolean = true,
)

public data class MessengerDesign(
  /** Gradient on the accent surfaces, as configured by the studio. */
  public val autoGradient: Boolean = true,
  public val radius: MessengerRadius = MessengerRadius.HIGH,
  public val bannerSource: MessengerBannerSource = MessengerBannerSource.PRESET,
  public val presetBanner: MessengerPresetBanner = MessengerPresetBanner.EMOJIS,
  public val bannerUrl: String? = null,
  /** Vertical crop anchor of a custom banner, 0 top to 100 bottom. */
  public val bannerFocusY: Float = 50f,
)

public data class MessengerContext(
  public val projectName: String = "",
  public val projectLogoUrl: String? = null,
  public val agentName: String = "",
  public val agentAvatarUrl: String? = null,
)

public data class MessengerConfig(
  public val branding: MessengerBranding = MessengerBranding(),
  public val modules: MessengerModules = MessengerModules(),
  public val messaging: MessengerMessaging = MessengerMessaging(),
  public val design: MessengerDesign = MessengerDesign(),
  public val context: MessengerContext = MessengerContext(),
  /** `0` means uncustomised: native theme, everything enabled. */
  public val version: Int = 0,
)

/** Customer identity on the Support side. */
public data class Customer(
  public val id: String,
  public val externalId: String? = null,
  public val name: String? = null,
  public val email: String? = null,
  public val avatarUrl: String? = null,
)

/** Attributes pushed by the host app, all optional. */
public data class SupportUserAttributes(
  public val email: String? = null,
  public val name: String? = null,
  public val avatarUrl: String? = null,
  public val language: String? = null,
  public val timezone: String? = null,
  public val location: String? = null,
)

/** One list page: the items and the next cursor, `null` at the end. */
public data class SupportPage<T>(
  public val items: List<T>,
  public val nextCursor: String?,
)
