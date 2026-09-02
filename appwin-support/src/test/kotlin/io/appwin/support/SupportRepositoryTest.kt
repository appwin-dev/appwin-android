package io.appwin.support

import io.appwin.core.network.ApiClient
import io.appwin.support.data.ApiSupportRepository
import io.appwin.support.domain.MessageAuthorType
import io.appwin.support.domain.MessengerRadius
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupportRepositoryTest {
  private lateinit var server: MockWebServer
  private lateinit var repository: ApiSupportRepository

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    val client = ApiClient(
      baseUrl = server.url("/").toString().trimEnd('/'),
      headers = mapOf("X-Appwin-App-Id" to "app-123"),
    )
    repository = ApiSupportRepository { client }
  }

  @After
  fun tearDown() = server.shutdown()

  @Test
  fun `la config applique les defauts sur une reponse minimale`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200)
        .setBody("""{"colors":{"primary":"#123456","primaryForeground":"#FFFFFF"},"version":3}"""),
    )

    val config = repository.config()

    assertEquals("#123456", config.branding.primaryHex)
    assertEquals(3, config.version)
    // Absent from the response: the FAQ stays enabled and the radius takes its
    // default. A studio that customised nothing must not end up with an
    // amputated messenger.
    assertTrue(config.modules.faqEnabled)
    assertEquals(MessengerRadius.HIGH, config.design.radius)
  }

  @Test
  fun `une banniere est ignoree quand la source dit aucune`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """{"colors":{"primary":"#000000","primaryForeground":"#FFFFFF"},
           "design":{"bannerSource":"none","bannerUrl":"https://x/y.png"},"version":1}""",
      ),
    )

    // The studio changed its mind without clearing the URL: the source is what
    // counts.
    assertNull(repository.config().design.bannerUrl)
  }

  @Test
  fun `la creation de conversation enveloppe le premier message`() = runTest {
    server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"c1"}"""))

    repository.createConversation("Bonjour")

    val body = server.takeRequest().body.readUtf8()
    assertTrue(body.contains(""""firstMessage""""))
    assertTrue(body.contains(""""body":"Bonjour""""))
  }

  @Test
  fun `un message du studio est reconnu comme tel`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """{"data":[{"id":"m1","authorType":"organization_member","authorNameSnapshot":"Léa",
           "body":"Bonjour","createdAt":"2026-08-26T10:00:00Z"}],"nextCursor":null}""",
      ),
    )

    val page = repository.messages("c1")

    assertEquals(MessageAuthorType.ORGANIZATION_MEMBER, page.items[0].authorType)
    assertTrue(page.items[0].authorType.isStudio)
    assertEquals("Léa", page.items[0].authorName)
  }

  @Test
  fun `une reponse IA compte comme un message du studio`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200)
        .setBody("""{"data":[{"id":"m1","authorType":"ai_assistant","body":"x"}]}"""),
    )
    assertTrue(repository.messages("c1").items[0].authorType.isStudio)
  }

  @Test
  fun `une categorie de FAQ sans article est masquee`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200)
        .setBody("""[{"id":"cat1","name":"Compte"},{"id":"cat2","name":"Vide"}]"""),
    )
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """[{"id":"f2","categoryId":"cat1","question":"B","answer":"b","position":2},
           {"id":"f1","categoryId":"cat1","question":"A","answer":"a","position":1}]""",
      ),
    )

    val groups = repository.faqGroups()

    assertEquals(1, groups.size)
    assertEquals("Compte", groups[0].category.name)
    // Articles follow the position the studio chose, not the response order.
    assertEquals(listOf("A", "B"), groups[0].articles.map { it.question })
  }

  @Test
  fun `une conversation lue apres son dernier message n a pas de pastille`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """{"data":[{"id":"c1","lastMessageAt":"2026-08-26T10:00:00Z",
           "lastReadAt":"2026-08-26T11:00:00Z"},
           {"id":"c2","lastMessageAt":"2026-08-26T12:00:00Z",
           "lastReadAt":"2026-08-26T11:00:00Z"}]}""",
      ),
    )

    val items = repository.conversations().items

    assertFalse(items[0].hasUnread)
    assertTrue(items[1].hasUnread)
  }
}
