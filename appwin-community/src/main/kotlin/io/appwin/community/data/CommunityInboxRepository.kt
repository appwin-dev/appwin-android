package io.appwin.community.data

import io.appwin.community.domain.CommunityNotificationType
import io.appwin.core.AppwinCore
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * One entry of the bell's inbox: a comment or a reply alone, or every like /
 * vote on one content grouped (« Marie and 3 others liked your post »).
 */
internal data class CommunityActivity(
  /** Newest notification of the entry. */
  val id: String,
  val type: CommunityNotificationType,
  /** Up to three, newest first. */
  val actors: List<Actor>,
  /** Distinct members behind the entry. */
  val actorCount: Int,
  val targetType: String,
  val targetId: String,
  val postId: String?,
  /** A reply's root comment: the tap opens its thread. */
  val threadCommentId: String?,
  val excerpt: String?,
  val isRead: Boolean,
  val createdAtMillis: Long,
) {
  data class Actor(val id: String, val nickname: String, val avatarUrl: String?)
}

/**
 * The bell's inbox: comments, replies, likes and votes around the member's
 * content, likes grouped per content. Internal, beside [CommunityRepository]
 * rather than in it: that interface is public.
 */
internal class CommunityInboxRepository(
  private val clientProvider: () -> ApiClient? = { AppwinCore.client },
) {
  private val client: ApiClient
    get() = clientProvider() ?: throw AppwinApiException.NotConfigured()

  suspend fun activity(limit: Int, offset: Int): List<CommunityActivity> = client.request(
    path = "$BASE/notifications/activity?limit=$limit&offset=$offset",
    method = HttpMethod.GET,
    deserializer = ListSerializer(CommunityActivityDto.serializer()),
  ).mapNotNull { it.toDomain() }

  private companion object {
    const val BASE = "/api/sdk/community/v1"
  }
}

@Serializable
internal data class CommunityActivityDto(
  val id: String,
  val type: String,
  val actors: List<CommunityNotificationDto.ActorDto> = emptyList(),
  val actorCount: Int = 0,
  val targetType: String = "",
  val targetId: String = "",
  val postId: String? = null,
  val threadCommentId: String? = null,
  val excerpt: String? = null,
  val isRead: Boolean = false,
  val createdAt: String? = null,
) {
  /** `null` when this binary does not know the type, like a notification. */
  fun toDomain(): CommunityActivity? {
    val kind = CommunityNotificationType.from(type) ?: return null
    return CommunityActivity(
      id = id,
      type = kind,
      actors = actors.map { CommunityActivity.Actor(it.id, it.nickname, it.avatarUrl?.takeIf { url -> url.isNotBlank() }) },
      actorCount = actorCount,
      targetType = targetType,
      targetId = targetId,
      postId = postId,
      threadCommentId = threadCommentId,
      excerpt = excerpt,
      isRead = isRead,
      createdAtMillis = IsoDate.toMillis(createdAt),
    )
  }
}
