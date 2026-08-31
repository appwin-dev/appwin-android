package io.appwin.support.data

import io.appwin.core.AppwinCore
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.CursorPage
import io.appwin.core.network.HttpMethod
import io.appwin.support.domain.Conversation
import io.appwin.support.domain.Customer
import io.appwin.support.domain.Faq
import io.appwin.support.domain.FaqCategory
import io.appwin.support.domain.FaqGroup
import io.appwin.support.domain.Message
import io.appwin.support.domain.MessengerConfig
import io.appwin.support.domain.SupportPage
import io.appwin.support.domain.SupportUserAttributes
import kotlinx.serialization.builtins.ListSerializer
import java.net.URLEncoder

/**
 * Support data access.
 *
 * An interface rather than a single class: the screens know only this contract,
 * which makes them testable with a double, without a server or a network.
 */
public interface SupportRepository {
  public suspend fun config(): MessengerConfig
  public suspend fun conversations(cursor: String? = null, limit: Int = 20): SupportPage<Conversation>
  public suspend fun conversation(id: String): Conversation
  public suspend fun createConversation(firstMessage: String): Conversation

  public suspend fun messages(
    conversationId: String,
    cursor: String? = null,
    limit: Int = 30,
  ): SupportPage<Message>

  public suspend fun sendMessage(conversationId: String, body: String): Message
  public suspend fun markRead(conversationId: String)
  public suspend fun toggleReaction(conversationId: String, messageId: String, emoji: String): Message

  public suspend fun faqGroups(): List<FaqGroup>

  public suspend fun identify(attributes: SupportUserAttributes): Customer
  public suspend fun registerPushToken(token: String, platform: String, pushOptIn: Boolean)
}

/** HTTP implementation, wired to Core's canonical client. */
public class ApiSupportRepository(
  private val clientProvider: () -> ApiClient? = { AppwinCore.client },
) : SupportRepository {

  private val client: ApiClient
    get() = clientProvider() ?: throw AppwinApiException.NotConfigured()

  private val json = ApiClient.json

  override suspend fun config(): MessengerConfig = client.request(
    path = "$BASE/config",
    method = HttpMethod.GET,
    deserializer = MessengerConfigDto.serializer(),
  ).toDomain()

  override suspend fun conversations(cursor: String?, limit: Int): SupportPage<Conversation> {
    val page: CursorPage<ConversationDto> = client.request(
      path = "$BASE/conversations${pageQuery(cursor, limit)}",
      method = HttpMethod.GET,
      deserializer = CursorPage.serializer(ConversationDto.serializer()),
    )
    return SupportPage(page.data.map { it.toDomain() }, page.nextCursor)
  }

  override suspend fun conversation(id: String): Conversation = client.request(
    path = "$BASE/conversations/${id.encoded()}",
    method = HttpMethod.GET,
    deserializer = ConversationDto.serializer(),
  ).toDomain()

  override suspend fun createConversation(firstMessage: String): Conversation = client.request(
    path = "$BASE/conversations",
    method = HttpMethod.POST,
    deserializer = ConversationDto.serializer(),
    body = json.encodeToString(
      CreateConversationBody.serializer(),
      CreateConversationBody(SendMessageBody(firstMessage)),
    ),
  ).toDomain()

  override suspend fun messages(
    conversationId: String,
    cursor: String?,
    limit: Int,
  ): SupportPage<Message> {
    val page: CursorPage<MessageDto> = client.request(
      path = "$BASE/conversations/${conversationId.encoded()}/messages${pageQuery(cursor, limit)}",
      method = HttpMethod.GET,
      deserializer = CursorPage.serializer(MessageDto.serializer()),
    )
    return SupportPage(page.data.map { it.toDomain() }, page.nextCursor)
  }

  override suspend fun sendMessage(conversationId: String, body: String): Message = client.request(
    path = "$BASE/conversations/${conversationId.encoded()}/messages",
    method = HttpMethod.POST,
    deserializer = MessageDto.serializer(),
    body = json.encodeToString(SendMessageBody.serializer(), SendMessageBody(body)),
  ).toDomain()

  override suspend fun markRead(conversationId: String) {
    client.requestVoid(
      path = "$BASE/conversations/${conversationId.encoded()}/messages/read",
      method = HttpMethod.POST,
    )
  }

  override suspend fun toggleReaction(
    conversationId: String,
    messageId: String,
    emoji: String,
  ): Message = client.request(
    path = "$BASE/conversations/${conversationId.encoded()}/messages/${messageId.encoded()}/reactions",
    method = HttpMethod.POST,
    deserializer = MessageDto.serializer(),
    body = json.encodeToString(ToggleReactionBody.serializer(), ToggleReactionBody(emoji)),
  ).toDomain()

  /**
   * Articles and categories, assembled client-side.
   *
   * Two calls rather than a dedicated endpoint: these are the two resources as
   * the server exposes them, and gluing them here avoids inventing a route for
   * a join of two short lists.
   */
  override suspend fun faqGroups(): List<FaqGroup> {
    val categories = client.request(
      path = "$BASE/faq-categories",
      method = HttpMethod.GET,
      deserializer = ListSerializer(FaqCategoryDto.serializer()),
    ).map { it.toDomain() }

    val articles = client.request(
      path = "$BASE/faqs",
      method = HttpMethod.GET,
      deserializer = ListSerializer(FaqDto.serializer()),
    ).map { it.toDomain() }

    val byCategory: Map<String, List<Faq>> = articles.groupBy { it.categoryId }

    return categories
      .map { category ->
        FaqGroup(
          category = category,
          articles = byCategory[category.id].orEmpty().sortedBy { it.position },
        )
      }
      // An empty category adds nothing to the help centre, so hide it rather
      // than show a heading followed by nothing.
      .filter { it.articles.isNotEmpty() }
  }

  override suspend fun identify(attributes: SupportUserAttributes): Customer = client.request(
    path = "$BASE/identify",
    method = HttpMethod.POST,
    deserializer = CustomerDto.serializer(),
    body = json.encodeToString(
      IdentifyBody.serializer(),
      IdentifyBody(
        email = attributes.email,
        name = attributes.name,
        avatarUrl = attributes.avatarUrl,
        language = attributes.language,
        timezone = attributes.timezone,
        location = attributes.location,
      ),
    ),
  ).toDomain()

  override suspend fun registerPushToken(token: String, platform: String, pushOptIn: Boolean) {
    client.requestVoid(
      path = "$BASE/push-token",
      method = HttpMethod.POST,
      body = json.encodeToString(
        PushTokenBody.serializer(),
        PushTokenBody(token, platform, pushOptIn),
      ),
    )
  }

  private fun pageQuery(cursor: String?, limit: Int): String = buildList {
    add("limit=$limit")
    cursor?.let { add("cursor=${it.encoded()}") }
  }.joinToString("&", prefix = "?")

  private companion object {
    const val BASE = "/api/sdk/support/v1"
  }
}

private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")
