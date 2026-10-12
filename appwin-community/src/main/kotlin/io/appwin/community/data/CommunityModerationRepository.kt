package io.appwin.community.data

import io.appwin.community.domain.CommunityNotification
import io.appwin.community.domain.ModerationAction
import io.appwin.community.domain.ModerationQueuePage
import io.appwin.community.domain.ModerationTargetType
import io.appwin.community.domain.PinSettings
import io.appwin.core.AppwinCore
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What moderators and admins do from the app, and the sanctions a member is
 * told about. Separate from [CommunityRepository]: most members never touch it.
 * The API refuses the moderation routes to plain members; the UI only offers
 * them to moderators.
 */
internal class CommunityModerationRepository(
  private val clientProvider: () -> ApiClient? = { AppwinCore.client },
) {
  private val client: ApiClient
    get() = clientProvider() ?: throw AppwinApiException.NotConfigured()

  private val json = ApiClient.json

  suspend fun queue(offset: Int, limit: Int): ModerationQueuePage = client.request(
    path = "$BASE/moderation/queue?limit=$limit&offset=$offset",
    method = HttpMethod.GET,
    deserializer = ModerationQueueDto.serializer(),
  ).toDomain()

  suspend fun decide(
    targetType: ModerationTargetType,
    targetId: String,
    action: ModerationAction,
    reason: String? = null,
    durationHours: Int? = null,
    reportIds: List<String> = emptyList(),
  ) {
    client.requestVoid(
      path = "$BASE/moderation/decisions",
      method = HttpMethod.POST,
      body = json.encodeToString(
        ModerationDecisionBody.serializer(),
        ModerationDecisionBody(targetType.wire, targetId, action.wire, reason, durationHours, reportIds),
      ),
    )
  }

  suspend fun moveToGroup(postId: String, groupId: String) =
    moderatePost(postId, buildJsonObject { put("groupId", groupId) }.toString())

  /** A criterion left out is sent as `null`: the server then clears it. */
  suspend fun pin(postId: String, settings: PinSettings) = moderatePost(
    postId,
    buildJsonObject {
      put("isPinned", true)
      put("pinnedUntil", settings.untilMillis?.let { JsonPrimitive(IsoDate.fromMillis(it)) } ?: JsonNull)
      put("pinMaxViewsPerMember", settings.maxViewsPerMember?.let(::JsonPrimitive) ?: JsonNull)
    }.toString(),
  )

  suspend fun unpin(postId: String) =
    moderatePost(postId, buildJsonObject { put("isPinned", false) }.toString())

  /** Removed content, warnings, bans: the cards behind the bell. */
  suspend fun sanctions(): List<CommunityNotification> = client.request(
    path = "$BASE/notifications?kind=sanctions&limit=50",
    method = HttpMethod.GET,
    deserializer = ListSerializer(CommunityNotificationDto.serializer()),
  ).mapNotNull { it.toDomain() }

  /** « J'ai compris ». */
  suspend fun acknowledge(notificationIds: List<String>) {
    if (notificationIds.isEmpty()) return
    client.requestVoid(
      path = "$BASE/notifications/read",
      method = HttpMethod.POST,
      body = json.encodeToString(
        MarkNotificationsReadBody.serializer(),
        MarkNotificationsReadBody(notificationIds),
      ),
    )
  }

  private suspend fun moderatePost(postId: String, body: String) {
    client.requestVoid(path = "$BASE/moderation/posts/$postId", method = HttpMethod.PATCH, body = body)
  }

  private companion object {
    const val BASE = "/api/sdk/community/v1"
  }
}
