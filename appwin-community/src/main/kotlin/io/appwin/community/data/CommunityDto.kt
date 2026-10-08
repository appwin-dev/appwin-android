package io.appwin.community.data

import io.appwin.community.domain.CommunityAuthor
import io.appwin.community.domain.CommunityBootstrap
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityGroup
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityMediaType
import io.appwin.community.domain.CommunityMemberRole
import io.appwin.community.domain.CommunityNotification
import io.appwin.community.domain.CommunityNotificationType
import io.appwin.community.domain.CommunityPoll
import io.appwin.community.domain.CommunityPollOption
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.domain.CommunityReactionCount
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.CommunityReactionResult
import kotlinx.serialization.Serializable

/**
 * Network DTOs and conversion to the domain.
 *
 * Strict boundary: the rest of the SDK never sees a DTO. ISO dates, enums and
 * missing values are handled here, once, and anything unreadable degrades
 * cleanly instead of failing the page.
 */

@Serializable
internal data class CommunityAuthorDto(
  val id: String,
  val nickname: String,
  val avatarUrl: String? = null,
  val role: String? = null,
  val isTeam: Boolean = false,
) {
  fun toDomain(): CommunityAuthor = CommunityAuthor(
    id = id,
    nickname = nickname,
    avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
    role = CommunityMemberRole.from(role),
    isTeam = isTeam,
  )
}

@Serializable
internal data class CommunityMediaDto(
  val url: String,
  val width: Int? = null,
  val height: Int? = null,
  val alt: String? = null,
  val type: String? = null,
) {
  /** `null` when the URL is unusable: the media disappears, not the post. */
  fun toDomain(): CommunityMedia? {
    if (url.isBlank()) return null
    return CommunityMedia(
      url = url,
      width = width,
      height = height,
      alt = alt,
      type = resolveMediaType(type, url),
    )
  }
}

/** Prefer wire `type`; fall back to extension when older payloads omit it. */
private fun resolveMediaType(wire: String?, url: String): CommunityMediaType {
  if (wire.equals("video", ignoreCase = true)) return CommunityMediaType.VIDEO
  if (wire.equals("image", ignoreCase = true)) return CommunityMediaType.IMAGE
  val path = url.substringBefore('?').lowercase()
  return if (path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".m4v")) {
    CommunityMediaType.VIDEO
  } else {
    CommunityMediaType.IMAGE
  }
}

@Serializable
internal data class CommunityPollOptionDto(
  val id: String,
  val text: String = "",
  val voteCount: Int = 0,
) {
  fun toDomain() = CommunityPollOption(id, text, voteCount)
}

@Serializable
internal data class CommunityPollDto(
  val options: List<CommunityPollOptionDto> = emptyList(),
  val totalVotes: Int = 0,
  val myOptionId: String? = null,
) {
  fun toDomain() = CommunityPoll(
    options = options.map { it.toDomain() },
    totalVotes = totalVotes,
    myOptionId = myOptionId,
  )
}

@Serializable
internal data class CommunityPostDto(
  val id: String,
  val groupId: String = "",
  val groupName: String = "",
  val author: CommunityAuthorDto? = null,
  val body: String = "",
  val translatedBody: String? = null,
  val sourceLanguage: String? = null,
  val media: List<CommunityMediaDto> = emptyList(),
  val poll: CommunityPollDto? = null,
  val isPinned: Boolean = false,
  val hasAdminTag: Boolean = false,
  val likeCount: Int = 0,
  val commentCount: Int = 0,
  val viewCount: Int = 0,
  val myReaction: String? = null,
  val topReactions: List<String> = emptyList(),
  val reactionCounts: List<CommunityReactionCountDto> = emptyList(),
  val canEdit: Boolean = false,
  val canDelete: Boolean = false,
  val isPendingReview: Boolean = false,
  val publishedAt: String? = null,
  val editedAt: String? = null,
  val isRemoved: Boolean = false,
) {
  fun toDomain(): CommunityPost = CommunityPost(
    id = id,
    groupId = groupId,
    groupName = groupName,
    author = author?.toDomain(),
    body = body,
    translatedBody = translatedBody,
    sourceLanguage = sourceLanguage,
    media = media.mapNotNull { it.toDomain() },
    poll = poll?.toDomain(),
    isPinned = isPinned,
    hasAdminTag = hasAdminTag,
    likeCount = likeCount,
    commentCount = commentCount,
    viewCount = viewCount,
    myReaction = CommunityReactionKind.from(myReaction),
    topReactions = topReactions.mapNotNull { CommunityReactionKind.from(it) },
    reactionCounts = reactionCounts.mapNotNull { it.toDomain() },
    canEdit = canEdit,
    canDelete = canDelete,
    isPendingReview = isPendingReview,
    publishedAtMillis = IsoDate.toMillis(publishedAt),
    editedAtMillis = editedAt?.let { IsoDate.toMillis(it) },
    isRemoved = isRemoved,
  )
}

@Serializable
internal data class CommunityCommentDto(
  val id: String,
  val postId: String = "",
  val parentCommentId: String? = null,
  val author: CommunityAuthorDto? = null,
  val body: String = "",
  val media: List<CommunityMediaDto> = emptyList(),
  val translatedBody: String? = null,
  val sourceLanguage: String? = null,
  val likeCount: Int = 0,
  val replyCount: Int = 0,
  val myReaction: String? = null,
  val topReactions: List<String> = emptyList(),
  val reactionCounts: List<CommunityReactionCountDto> = emptyList(),
  val canEdit: Boolean = false,
  val canDelete: Boolean = false,
  val isPendingReview: Boolean = false,
  val replies: List<CommunityCommentDto> = emptyList(),
  val createdAt: String? = null,
  val editedAt: String? = null,
  val isRemoved: Boolean = false,
) {
  fun toDomain(): CommunityComment = CommunityComment(
    id = id,
    postId = postId,
    parentCommentId = parentCommentId,
    author = author?.toDomain(),
    body = body,
    media = media.mapNotNull { it.toDomain() },
    translatedBody = translatedBody,
    sourceLanguage = sourceLanguage,
    likeCount = likeCount,
    replyCount = replyCount,
    myReaction = CommunityReactionKind.from(myReaction),
    topReactions = topReactions.mapNotNull { CommunityReactionKind.from(it) },
    reactionCounts = reactionCounts.mapNotNull { it.toDomain() },
    canEdit = canEdit,
    canDelete = canDelete,
    isPendingReview = isPendingReview,
    replies = replies.map { it.toDomain() },
    createdAtMillis = IsoDate.toMillis(createdAt),
    editedAtMillis = editedAt?.let { IsoDate.toMillis(it) },
    isRemoved = isRemoved,
  )
}

@Serializable
internal data class CommunityProfileDto(
  val id: String,
  val nickname: String = "",
  val bio: String? = null,
  val avatarUrl: String? = null,
  val role: String? = null,
  val isTeam: Boolean = false,
  val isAnonymous: Boolean = true,
  val postCount: Int = 0,
  val commentCount: Int = 0,
  val receivedReactionCount: Int = 0,
  val joinedAt: String? = null,
  val isMe: Boolean = false,
  val isBanned: Boolean = false,
) {
  fun toDomain(): CommunityProfile = CommunityProfile(
    id = id,
    nickname = nickname,
    bio = bio,
    avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
    role = CommunityMemberRole.from(role),
    isTeam = isTeam,
    isAnonymous = isAnonymous,
    postCount = postCount,
    commentCount = commentCount,
    receivedReactionCount = receivedReactionCount,
    joinedAtMillis = IsoDate.toMillis(joinedAt),
    isMe = isMe,
    isBanned = isBanned,
  )
}

@Serializable
internal data class CommunityGroupDto(
  val id: String,
  val name: String = "",
  val description: String? = null,
  val emoji: String? = null,
  val imageUrl: String? = null,
  val isDefault: Boolean = false,
  val canPost: Boolean = true,
  val postCount: Int = 0,
) {
  fun toDomain(): CommunityGroup = CommunityGroup(
    id, name, description, emoji, imageUrl?.takeIf { it.isNotBlank() },
    isDefault, canPost, postCount,
  )
}

@Serializable
internal data class CommunityBootstrapDto(
  val config: CommunityConfigDto = CommunityConfigDto(),
  val groups: List<CommunityGroupDto> = emptyList(),
  val profile: CommunityProfileDto,
  val unreadNotificationCount: Int = 0,
  val unreadSanctionCount: Int = 0,
  val moderationPendingCount: Int = 0,
) {
  fun toDomain(): CommunityBootstrap = CommunityBootstrap(
    config = config.toDomain(),
    groups = groups.map { it.toDomain() },
    profile = profile.toDomain(),
    unreadNotificationCount = unreadNotificationCount,
    unreadSanctionCount = unreadSanctionCount,
    moderationPendingCount = moderationPendingCount,
  )
}

@Serializable
internal data class CommunityNotificationDto(
  val id: String,
  val type: String,
  val actor: ActorDto? = null,
  val targetType: String = "",
  val targetId: String = "",
  val postId: String? = null,
  val excerpt: String? = null,
  val isRead: Boolean = false,
  val createdAt: String? = null,
  val reason: String? = null,
  val sanctionUntil: String? = null,
) {
  @Serializable
  data class ActorDto(val id: String, val nickname: String, val avatarUrl: String? = null)

  fun toDomain(): CommunityNotification? {
    val kind = CommunityNotificationType.from(type) ?: return null
    return CommunityNotification(
      id = id,
      type = kind,
      actorId = actor?.id,
      actorNickname = actor?.nickname,
      actorAvatarUrl = actor?.avatarUrl?.takeIf { it.isNotBlank() },
      targetType = targetType,
      targetId = targetId,
      postId = postId,
      excerpt = excerpt,
      isRead = isRead,
      createdAtMillis = IsoDate.toMillis(createdAt),
      reason = reason?.takeIf { it.isNotBlank() },
      sanctionUntilMillis = sanctionUntil?.let { IsoDate.toMillis(it) },
    )
  }
}

@Serializable
internal data class CommunityReactionCountDto(
  val kind: String = "",
  val count: Int = 0,
) {
  fun toDomain(): CommunityReactionCount? {
    val parsed = CommunityReactionKind.from(kind) ?: return null
    if (count <= 0) return null
    return CommunityReactionCount(parsed, count)
  }
}

@Serializable
internal data class CommunityReactionResultDto(
  val targetId: String,
  val myReaction: String? = null,
  val topReactions: List<String> = emptyList(),
  val reactionCounts: List<CommunityReactionCountDto> = emptyList(),
  val likeCount: Int = 0,
) {
  fun toDomain(): CommunityReactionResult =
    CommunityReactionResult(
      targetId,
      CommunityReactionKind.from(myReaction),
      topReactions.mapNotNull { CommunityReactionKind.from(it) },
      reactionCounts.mapNotNull { it.toDomain() },
      likeCount,
    )
}

@Serializable
internal data class CommunityTranslationDto(
  val targetType: String = "",
  val targetId: String = "",
  val translatedBody: String = "",
  val sourceLanguage: String? = null,
  val targetLanguage: String = "",
)

/* ---------------------------------------------------------------------- */
/* Request bodies                                                         */
/* ---------------------------------------------------------------------- */

@Serializable
internal data class MediaInputDto(
  val url: String,
  val width: Int? = null,
  val height: Int? = null,
  val alt: String? = null,
  val type: String = "image",
)

@Serializable
internal data class CreatePostBody(
  val groupId: String? = null,
  val body: String,
  val media: List<MediaInputDto> = emptyList(),
  val poll: CreatePollBody? = null,
)

@Serializable
internal data class CreatePollBody(val options: List<String>)

@Serializable
internal data class PollVoteBody(val optionId: String)

@Serializable
internal data class PollVoteResultDto(
  val postId: String,
  val poll: CommunityPollDto,
)

@Serializable
internal data class UpdatePostBody(
  val body: String? = null,
  val groupId: String? = null,
  val media: List<MediaInputDto>? = null,
)

@Serializable
internal data class CreateCommentBody(
  val body: String,
  val parentCommentId: String? = null,
  val media: List<MediaInputDto> = emptyList(),
)

@Serializable
internal data class ToggleReactionBody(val kind: String)

@Serializable
internal data class TrackViewsBody(val postIds: List<String>)

@Serializable
internal data class ReportBody(
  val targetType: String,
  val targetId: String,
  val reason: String,
  val note: String? = null,
)

@Serializable
internal data class SetUserBody(
  val nickname: String? = null,
  val avatarUrl: String? = null,
  val bio: String? = null,
)

@Serializable
internal data class UpdateProfileBody(
  val nickname: String? = null,
  val bio: String? = null,
  val avatarUrl: String? = null,
  val isAnonymous: Boolean? = null,
)

@Serializable
internal data class SignCommunityUploadBody(
  val mimeType: String,
  val sizeBytes: Int,
)

@Serializable
internal data class SignCommunityUploadResponse(
  val uploadId: String,
  val storageKey: String,
  val publicUrl: String,
  val postUrl: String,
  val fields: Map<String, String> = emptyMap(),
  val expiresInSec: Int = 300,
)

@Serializable
internal data class ConfirmCommunityUploadResponse(
  val uploadId: String,
  val storageKey: String,
  val publicUrl: String,
  val mimeType: String,
  val sizeBytes: Int = 0,
  val fileName: String? = null,
)

@Serializable
internal data class MarkNotificationsReadBody(val notificationIds: List<String>)

@Serializable
internal data class TranslateBody(
  val targetType: String,
  val targetId: String,
  val targetLanguage: String? = null,
)
