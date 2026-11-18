package io.appwin.community.data

import io.appwin.community.domain.MemberContact
import io.appwin.community.domain.ModerationContentStatus
import io.appwin.community.domain.ModerationDecisionInfo
import io.appwin.community.domain.ModerationQueueItem
import io.appwin.community.domain.ModerationQueuePage
import io.appwin.community.domain.ModerationReport
import io.appwin.community.domain.ModerationTargetType
import kotlinx.serialization.Serializable

// In-app moderation DTOs, kept apart from the feed's: most members never load them.

@Serializable
internal data class ModerationQueueDto(
  val items: List<ItemDto> = emptyList(),
  val pendingCount: Int = 0,
  val hasMore: Boolean = false,
) {
  @Serializable
  data class ItemDto(
    val targetType: String,
    val targetId: String,
    val contentStatus: String? = null,
    val aiCategories: List<String> = emptyList(),
    val reports: List<ReportDto> = emptyList(),
    val post: CommunityPostDto,
    val comment: CommunityCommentDto? = null,
    /** Absent from APIs older than the « Traités » tab. */
    val decision: DecisionDto? = null,
  ) {
    /** `null` for a target type this binary does not know. */
    fun toDomain(): ModerationQueueItem? {
      val type = ModerationTargetType.from(targetType) ?: return null
      return ModerationQueueItem(
        targetType = type,
        targetId = targetId,
        status = ModerationContentStatus.from(contentStatus),
        aiCategories = aiCategories,
        reports = reports.map { it.toDomain() },
        post = post.toDomain(),
        comment = comment?.toDomain(),
        decision = decision?.toDomain(),
      )
    }
  }

  @Serializable
  data class DecisionDto(
    val action: String,
    val actorName: String? = null,
    val automated: Boolean = false,
    val at: String? = null,
  ) {
    fun toDomain(): ModerationDecisionInfo? {
      val millis = IsoDate.toMillis(at).takeIf { it > 0 } ?: return null
      return ModerationDecisionInfo(action, actorName, automated, millis)
    }
  }

  @Serializable
  data class ReportDto(val id: String, val reason: String = "", val createdAt: String? = null) {
    fun toDomain() = ModerationReport(id, reason, IsoDate.toMillis(createdAt))
  }

  fun toDomain() = ModerationQueuePage(items.mapNotNull { it.toDomain() }, pendingCount, hasMore)
}

@Serializable
internal data class ModerationDecisionBody(
  val targetType: String,
  val targetId: String,
  val action: String,
  val reason: String? = null,
  val durationHours: Int? = null,
  val reportIds: List<String> = emptyList(),
)

/** The contact fields of a profile, sent by the API to moderators and admins only. */
@Serializable
internal data class MemberContactDto(val email: String? = null, val externalId: String? = null) {
  fun toDomain() = MemberContact(email?.takeIf { it.isNotBlank() }, externalId?.takeIf { it.isNotBlank() })
}
