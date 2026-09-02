package io.appwin.community.data

import io.appwin.community.domain.CommunityBootstrap
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityFeedSort
import io.appwin.community.domain.CommunityGroup
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityNotification
import io.appwin.community.domain.CommunityPage
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.CommunityReactionResult
import io.appwin.community.domain.CommunityReportReason
import io.appwin.core.AppwinCore
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.CursorPage
import io.appwin.core.network.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import java.net.URLEncoder
import java.util.Locale

/**
 * Community data access.
 *
 * An interface rather than a single class: the screens know only this contract,
 * which makes them testable with a double, without a server or a network.
 */
public interface CommunityRepository {
  public suspend fun bootstrap(): CommunityBootstrap

  public suspend fun feed(
    groupId: String? = null,
    sort: CommunityFeedSort = CommunityFeedSort.RECENT,
    cursor: String? = null,
    limit: Int = 20,
  ): CommunityPage<CommunityPost>

  public suspend fun createPost(
    groupId: String?,
    body: String,
    media: List<CommunityMedia> = emptyList(),
  ): CommunityPost

  public suspend fun updatePost(postId: String, body: String): CommunityPost
  public suspend fun deletePost(postId: String)

  public suspend fun comments(postId: String, limit: Int = 50, offset: Int = 0): List<CommunityComment>
  public suspend fun createComment(
    postId: String,
    body: String,
    parentCommentId: String? = null,
  ): CommunityComment

  public suspend fun deleteComment(commentId: String)

  public suspend fun reactToPost(postId: String, kind: CommunityReactionKind): CommunityReactionResult
  public suspend fun reactToComment(
    commentId: String,
    kind: CommunityReactionKind,
  ): CommunityReactionResult

  public suspend fun trackViews(postIds: List<String>)

  public suspend fun profile(profileId: String): CommunityProfile
  public suspend fun setUser(
    nickname: String? = null,
    avatarUrl: String? = null,
    bio: String? = null,
  ): CommunityProfile

  public suspend fun updateOwnProfile(
    nickname: String? = null,
    bio: String? = null,
    avatarUrl: String? = null,
    isAnonymous: Boolean? = null,
  ): CommunityProfile

  public suspend fun report(
    targetType: String,
    targetId: String,
    reason: CommunityReportReason,
    note: String? = null,
  )

  public suspend fun notifications(limit: Int = 30, offset: Int = 0): List<CommunityNotification>
  public suspend fun markNotificationsRead(ids: List<String>)
  public suspend fun translate(targetType: String, targetId: String): String
  public suspend fun groups(): List<CommunityGroup>
}

/** HTTP implementation, wired to Core's canonical client. */
public class ApiCommunityRepository(
  private val clientProvider: () -> ApiClient? = { AppwinCore.client },
) : CommunityRepository {

  private val client: ApiClient
    get() = clientProvider() ?: throw AppwinApiException.NotConfigured()

  private val json = ApiClient.json

  /**
   * Reading language, sent on every request that can return translated text. It
   * depends on the phone rather than the project, so it belongs in a per-request
   * header, not in the canonical ones.
   */
  private val languageHeader: Map<String, String>
    get() = mapOf("X-Appwin-Language" to Locale.getDefault().language)

  override suspend fun bootstrap(): CommunityBootstrap = client.request(
    path = "$BASE/bootstrap",
    method = HttpMethod.GET,
    deserializer = CommunityBootstrapDto.serializer(),
    extraHeaders = languageHeader,
  ).toDomain()

  override suspend fun groups(): List<CommunityGroup> = bootstrap().groups

  override suspend fun feed(
    groupId: String?,
    sort: CommunityFeedSort,
    cursor: String?,
    limit: Int,
  ): CommunityPage<CommunityPost> {
    val query = buildList {
      add("limit=$limit")
      add("sort=${sort.wire}")
      groupId?.let { add("groupId=${it.encoded()}") }
      cursor?.let { add("cursor=${it.encoded()}") }
    }.joinToString("&", prefix = "?")

    val page: CursorPage<CommunityPostDto> = client.request(
      path = "$BASE/feed$query",
      method = HttpMethod.GET,
      deserializer = CursorPage.serializer(CommunityPostDto.serializer()),
      extraHeaders = languageHeader,
    )
    return CommunityPage(page.data.map { it.toDomain() }, page.nextCursor)
  }

  override suspend fun createPost(
    groupId: String?,
    body: String,
    media: List<CommunityMedia>,
  ): CommunityPost = client.request(
    path = "$BASE/posts",
    method = HttpMethod.POST,
    deserializer = CommunityPostDto.serializer(),
    body = json.encodeToString(
      CreatePostBody.serializer(),
      CreatePostBody(
        groupId = groupId,
        body = body,
        media = media.map { MediaInputDto(it.url, it.width, it.height, it.alt) },
      ),
    ),
  ).toDomain()

  override suspend fun updatePost(postId: String, body: String): CommunityPost = client.request(
    path = "$BASE/posts/${postId.encoded()}",
    method = HttpMethod.PATCH,
    deserializer = CommunityPostDto.serializer(),
    body = json.encodeToString(UpdatePostBody.serializer(), UpdatePostBody(body)),
  ).toDomain()

  override suspend fun deletePost(postId: String) {
    client.requestVoid("$BASE/posts/${postId.encoded()}", HttpMethod.DELETE)
  }

  override suspend fun comments(postId: String, limit: Int, offset: Int): List<CommunityComment> =
    client.request(
      path = "$BASE/posts/${postId.encoded()}/comments?limit=$limit&offset=$offset",
      method = HttpMethod.GET,
      deserializer = ListSerializer(CommunityCommentDto.serializer()),
      extraHeaders = languageHeader,
    ).map { it.toDomain() }

  override suspend fun createComment(
    postId: String,
    body: String,
    parentCommentId: String?,
  ): CommunityComment = client.request(
    path = "$BASE/posts/${postId.encoded()}/comments",
    method = HttpMethod.POST,
    deserializer = CommunityCommentDto.serializer(),
    body = json.encodeToString(
      CreateCommentBody.serializer(),
      CreateCommentBody(body, parentCommentId),
    ),
  ).toDomain()

  override suspend fun deleteComment(commentId: String) {
    client.requestVoid("$BASE/comments/${commentId.encoded()}", HttpMethod.DELETE)
  }

  override suspend fun reactToPost(
    postId: String,
    kind: CommunityReactionKind,
  ): CommunityReactionResult = toggleReaction("$BASE/posts/${postId.encoded()}/reactions", kind)

  override suspend fun reactToComment(
    commentId: String,
    kind: CommunityReactionKind,
  ): CommunityReactionResult =
    toggleReaction("$BASE/comments/${commentId.encoded()}/reactions", kind)

  private suspend fun toggleReaction(
    path: String,
    kind: CommunityReactionKind,
  ): CommunityReactionResult = client.request(
    path = path,
    method = HttpMethod.POST,
    deserializer = CommunityReactionResultDto.serializer(),
    body = json.encodeToString(ToggleReactionBody.serializer(), ToggleReactionBody(kind.wire)),
  ).toDomain()

  override suspend fun trackViews(postIds: List<String>) {
    if (postIds.isEmpty()) return
    client.requestVoid(
      path = "$BASE/views",
      method = HttpMethod.POST,
      body = json.encodeToString(TrackViewsBody.serializer(), TrackViewsBody(postIds)),
    )
  }

  override suspend fun profile(profileId: String): CommunityProfile = client.request(
    path = "$BASE/profiles/${profileId.encoded()}",
    method = HttpMethod.GET,
    deserializer = CommunityProfileDto.serializer(),
  ).toDomain()

  override suspend fun setUser(
    nickname: String?,
    avatarUrl: String?,
    bio: String?,
  ): CommunityProfile = client.request(
    path = "$BASE/me",
    method = HttpMethod.POST,
    deserializer = CommunityProfileDto.serializer(),
    body = json.encodeToString(SetUserBody.serializer(), SetUserBody(nickname, avatarUrl, bio)),
  ).toDomain()

  override suspend fun updateOwnProfile(
    nickname: String?,
    bio: String?,
    avatarUrl: String?,
    isAnonymous: Boolean?,
  ): CommunityProfile = client.request(
    path = "$BASE/me",
    method = HttpMethod.PATCH,
    deserializer = CommunityProfileDto.serializer(),
    body = json.encodeToString(
      UpdateProfileBody.serializer(),
      UpdateProfileBody(nickname, bio, avatarUrl, isAnonymous),
    ),
  ).toDomain()

  override suspend fun report(
    targetType: String,
    targetId: String,
    reason: CommunityReportReason,
    note: String?,
  ) {
    client.requestVoid(
      path = "$BASE/reports",
      method = HttpMethod.POST,
      body = json.encodeToString(
        ReportBody.serializer(),
        ReportBody(targetType, targetId, reason.wire, note),
      ),
    )
  }

  override suspend fun notifications(limit: Int, offset: Int): List<CommunityNotification> =
    client.request(
      path = "$BASE/notifications?limit=$limit&offset=$offset",
      method = HttpMethod.GET,
      deserializer = ListSerializer(CommunityNotificationDto.serializer()),
    ).mapNotNull { it.toDomain() }

  override suspend fun markNotificationsRead(ids: List<String>) {
    if (ids.isEmpty()) return
    client.requestVoid(
      path = "$BASE/notifications/read",
      method = HttpMethod.POST,
      body = json.encodeToString(
        MarkNotificationsReadBody.serializer(),
        MarkNotificationsReadBody(ids),
      ),
    )
  }

  override suspend fun translate(targetType: String, targetId: String): String = client.request(
    path = "$BASE/translate",
    method = HttpMethod.POST,
    deserializer = CommunityTranslationDto.serializer(),
    body = json.encodeToString(
      TranslateBody.serializer(),
      TranslateBody(targetType, targetId, Locale.getDefault().language),
    ),
  ).translatedBody

  private companion object {
    const val BASE = "/api/sdk/community/v1"
  }
}

private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")
