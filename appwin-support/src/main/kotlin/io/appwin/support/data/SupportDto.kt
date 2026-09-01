package io.appwin.support.data

import io.appwin.support.domain.Attachment
import io.appwin.support.domain.Conversation
import io.appwin.support.domain.ConversationStatus
import io.appwin.support.domain.Customer
import io.appwin.support.domain.Faq
import io.appwin.support.domain.FaqCategory
import io.appwin.support.domain.Message
import io.appwin.support.domain.MessageAuthorType
import io.appwin.support.domain.MessageReaction
import io.appwin.support.domain.MessengerBranding
import io.appwin.support.domain.MessengerConfig
import io.appwin.support.domain.MessengerContext
import io.appwin.support.domain.MessengerDesign
import io.appwin.support.domain.MessengerMessaging
import io.appwin.support.domain.MessengerModules
import io.appwin.support.domain.MessengerRadius
import kotlinx.serialization.Serializable

/**
 * Parses an ISO-8601 date into epoch milliseconds.
 *
 * Hand-written rather than delegated to `java.time`: `Instant.parse` needs API
 * 26 and the SDK targets API 24. Desugaring would fix it but would impose itself
 * on every integrating app, to parse a string of known shape.
 * forme connue.
 *
 * Returns `0` on an unreadable date: a badly dated message shows up in the wrong
 * place, it does not wipe the conversation.
 */
internal object IsoDate {
  private val PATTERN = Regex(
    """(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|z|[+-]\d{2}:?\d{2})?""",
  )

  fun toMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0
    val match = PATTERN.matchEntire(raw.trim()) ?: return 0
    val (y, mo, d, h, mi, s) = match.destructured

    val days = daysFromCivil(y.toInt(), mo.toInt(), d.toInt())
    var millis = days * 86_400_000L +
      h.toInt() * 3_600_000L +
      mi.toInt() * 60_000L +
      s.toInt() * 1_000L

    match.groupValues.getOrNull(7)?.takeIf { it.isNotEmpty() }?.let { fraction ->
      millis += fraction.padEnd(3, '0').take(3).toLong()
    }

    match.groupValues.getOrNull(8)?.takeIf { it.isNotEmpty() && !it.equals("Z", true) }
      ?.let { offset ->
        val sign = if (offset.startsWith("-")) 1 else -1
        val digits = offset.drop(1).replace(":", "")
        millis += sign * (digits.take(2).toLong() * 3_600_000L + digits.drop(2).toLong() * 60_000L)
      }

    return millis
  }

  /** Days since 1970-01-01, using Howard Hinnant's civil algorithm. */
  private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
  }
}

@Serializable
internal data class ConversationDto(
  val id: String,
  val preview: String? = null,
  val status: String = "open",
  val isUrgent: Boolean = false,
  val isFavorite: Boolean = false,
  val lastMessageAt: String? = null,
  val lastReadAt: String? = null,
  val createdAt: String? = null,
) {
  fun toDomain(): Conversation = Conversation(
    id = id,
    preview = preview,
    status = ConversationStatus.from(status),
    lastMessageAtMillis = lastMessageAt?.let { IsoDate.toMillis(it) },
    lastReadAtMillis = lastReadAt?.let { IsoDate.toMillis(it) },
    createdAtMillis = IsoDate.toMillis(createdAt),
  )
}

@Serializable
internal data class AttachmentDto(
  val id: String,
  val messageId: String = "",
  val filename: String = "",
  val mimeType: String = "",
  val sizeBytes: Int = 0,
  val url: String = "",
) {
  /** `null` when the URL is empty: the attachment disappears, not the message. */
  fun toDomain(): Attachment? =
    if (url.isBlank()) null else Attachment(id, messageId, filename, mimeType, sizeBytes, url)
}

@Serializable
internal data class MessageReactionDto(
  val emoji: String,
  val count: Int = 0,
  val reactedByMe: Boolean = false,
) {
  fun toDomain(): MessageReaction = MessageReaction(emoji, count, reactedByMe)
}

@Serializable
internal data class MessageDto(
  val id: String,
  val conversationId: String = "",
  val authorType: String = "customer",
  val authorId: String = "",
  val authorNameSnapshot: String? = null,
  val body: String = "",
  val readAt: String? = null,
  val createdAt: String? = null,
  val attachments: List<AttachmentDto>? = null,
  val reactions: List<MessageReactionDto>? = null,
) {
  fun toDomain(): Message = Message(
    id = id,
    authorType = MessageAuthorType.from(authorType),
    authorName = authorNameSnapshot,
    body = body,
    readAtMillis = readAt?.let { IsoDate.toMillis(it) },
    createdAtMillis = IsoDate.toMillis(createdAt),
    attachments = attachments.orEmpty().mapNotNull { it.toDomain() },
    reactions = reactions.orEmpty().map { it.toDomain() },
  )
}

@Serializable
internal data class FaqDto(
  val id: String,
  val categoryId: String = "",
  val question: String = "",
  val answer: String = "",
  val position: Int? = null,
) {
  fun toDomain(): Faq = Faq(id, categoryId, question, answer, position ?: 0)
}

@Serializable
internal data class FaqCategoryDto(val id: String, val name: String = "") {
  fun toDomain(): FaqCategory = FaqCategory(id, name)
}

@Serializable
internal data class CustomerDto(
  val id: String,
  val externalId: String? = null,
  val name: String? = null,
  val email: String? = null,
  val avatarUrl: String? = null,
) {
  fun toDomain(): Customer = Customer(id, externalId, name, email, avatarUrl)
}

@Serializable
internal data class MessengerConfigDto(
  val colors: Colors = Colors(),
  val modules: Modules? = null,
  val messaging: Messaging? = null,
  val design: Design? = null,
  val context: Context? = null,
  val version: Int = 0,
) {
  @Serializable
  data class Colors(
    val primary: String = "#F97316",
    val primaryForeground: String = "#FFFFFF",
  )

  @Serializable
  data class Modules(val faqEnabled: Boolean = true)

  @Serializable
  data class Messaging(
    val agentName: String? = null,
    val avatarSource: String? = null,
    val agentAvatarUrl: String? = null,
    val welcomeMessage: String? = null,
    val welcomeMessageEnabled: Boolean? = null,
  )

  @Serializable
  data class Design(
    val autoGradient: Boolean? = null,
    val radius: String? = null,
    val bannerSource: String? = null,
    val presetBannerId: String? = null,
    val bannerUrl: String? = null,
    val bannerFocusY: Double? = null,
  )

  @Serializable
  data class Context(
    val projectName: String = "",
    val projectLogoUrl: String? = null,
    val agentName: String = "",
    val agentAvatarUrl: String? = null,
    val assetsBaseUrl: String = "",
  )

  fun toDomain(): MessengerConfig = MessengerConfig(
    branding = MessengerBranding(colors.primary, colors.primaryForeground),
    modules = MessengerModules(modules?.faqEnabled ?: true),
    messaging = MessengerMessaging(
      agentName = messaging?.agentName?.takeIf { it.isNotBlank() },
      agentAvatarUrl = messaging?.agentAvatarUrl?.takeIf { it.isNotBlank() },
      welcomeMessage = messaging?.welcomeMessage?.takeIf { it.isNotBlank() },
      welcomeMessageEnabled = messaging?.welcomeMessageEnabled ?: false,
    ),
    design = MessengerDesign(
      radius = MessengerRadius.from(design?.radius),
      // A banner is only kept when the source asks for one: the studio may have
      // left a URL behind after switching to "none".
      bannerUrl = design?.bannerUrl?.takeIf {
        it.isNotBlank() && design.bannerSource != "none"
      },
    ),
    context = MessengerContext(
      projectName = context?.projectName.orEmpty(),
      projectLogoUrl = context?.projectLogoUrl?.takeIf { it.isNotBlank() },
      agentName = context?.agentName.orEmpty(),
      agentAvatarUrl = context?.agentAvatarUrl?.takeIf { it.isNotBlank() },
    ),
    version = version,
  )
}

/* ---------------------------------------------------------------------- */
/* Request bodies                                                         */
/* ---------------------------------------------------------------------- */

@Serializable
internal data class CreateConversationBody(val firstMessage: SendMessageBody)

@Serializable
internal data class SendMessageBody(val body: String)

@Serializable
internal data class ToggleReactionBody(val emoji: String)

@Serializable
internal data class IdentifyBody(
  val email: String? = null,
  val name: String? = null,
  val avatarUrl: String? = null,
  val language: String? = null,
  val timezone: String? = null,
  val location: String? = null,
)

@Serializable
internal data class PushTokenBody(
  val token: String,
  val platform: String,
  val pushOptIn: Boolean,
)
