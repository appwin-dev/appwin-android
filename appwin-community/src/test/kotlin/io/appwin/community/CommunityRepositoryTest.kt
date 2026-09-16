package io.appwin.community

import io.appwin.community.data.ApiCommunityRepository
import io.appwin.community.data.IsoDate
import io.appwin.community.domain.CommunityFeedSort
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.CommunityReportReason
import io.appwin.core.network.ApiClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommunityRepositoryTest {
  private lateinit var server: MockWebServer
  private lateinit var repository: ApiCommunityRepository

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    val client = ApiClient(
      baseUrl = server.url("/").toString().trimEnd('/'),
      headers = mapOf("X-Appwin-App-Id" to "app-123"),
    )
    repository = ApiCommunityRepository { client }
  }

  @After
  fun tearDown() = server.shutdown()

  @Test
  fun `le fil decode une page et son curseur`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """{"data":[{"id":"p1","groupId":"g1","groupName":"General","body":"Salut",
           "publishedAt":"2026-08-26T10:00:00.000Z","likeCount":3,"myReaction":"like"}],
           "nextCursor":"cur-2"}""",
      ),
    )

    val page = repository.feed(groupId = "g1", sort = CommunityFeedSort.TOP)

    assertEquals(1, page.items.size)
    assertEquals("cur-2", page.nextCursor)
    assertEquals(CommunityReactionKind.LIKE, page.items[0].myReaction)

    val path = server.takeRequest().path!!
    assertTrue(path.contains("sort=top"))
    assertTrue(path.contains("groupId=g1"))
  }

  @Test
  fun `une reaction inconnue est ignoree sans casser le decodage`() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """{"data":[{"id":"p1","body":"x","myReaction":"applause"}],"nextCursor":null}""",
      ),
    )

    // A type added server-side must not empty the feed of binaries already
    // installed on phones.
    val page = repository.feed()
    assertEquals(1, page.items.size)
    assertNull(page.items[0].myReaction)
  }

  @Test
  fun `la langue de lecture est envoyee sur le fil`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
    repository.feed()
    assertTrue(server.takeRequest().getHeader("X-Appwin-Language")!!.isNotEmpty())
  }

  @Test
  fun `un signalement envoie le motif attendu par le serveur`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    repository.report("post", "p1", CommunityReportReason.HATE_SPEECH)

    val body = server.takeRequest().body.readUtf8()
    // `hate_speech`, not `HATE_SPEECH`: the server validates this form.
    assertTrue(body.contains(""""reason":"hate_speech""""))
  }

  @Test
  fun `trackViews n appelle pas le serveur sans post`() = runTest {
    repository.trackViews(emptyList())
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `les dates ISO sont analysees avec et sans fraction`() {
    val withFraction = IsoDate.toMillis("2026-08-26T10:00:00.123Z")
    val without = IsoDate.toMillis("2026-08-26T10:00:00Z")

    assertEquals(1787738400000L, without)
    assertEquals(without + 123, withFraction)
    // An unreadable date must never throw: it is worth 0.
    assertEquals(0L, IsoDate.toMillis("pas une date"))
    assertEquals(0L, IsoDate.toMillis(null))
  }

  @Test
  fun `un decalage horaire est ramene en UTC`() {
    val utc = IsoDate.toMillis("2026-08-26T10:00:00Z")
    assertEquals(utc, IsoDate.toMillis("2026-08-26T12:00:00+02:00"))
  }
}
