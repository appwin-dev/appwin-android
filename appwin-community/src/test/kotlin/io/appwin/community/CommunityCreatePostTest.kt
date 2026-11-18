package io.appwin.community

import io.appwin.community.data.ApiCommunityRepository
import io.appwin.core.network.ApiClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommunityCreatePostTest {
  private lateinit var server: MockWebServer
  private lateinit var repository: ApiCommunityRepository

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    val client = ApiClient(baseUrl = server.url("/").toString().trimEnd('/'), headers = emptyMap())
    repository = ApiCommunityRepository { client }
  }

  @After
  fun tearDown() = server.shutdown()

  @Test
  fun `the text is trimmed and required`() {
    assertEquals("Super séance", validateCreatePost("  Super séance \n", 0))
    val error = assertThrows(IllegalArgumentException::class.java) { validateCreatePost("  \n ", 1) }
    assertTrue(error.message!!.contains("text is empty"))
  }

  @Test
  fun `more than ten images is refused before any request`() {
    validateCreatePost("ok", 10)
    assertThrows(IllegalArgumentException::class.java) { validateCreatePost("ok", 11) }
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `the status follows what moderation decided`() = runTest {
    server.enqueue(MockResponse().setBody(post("p1", "")))
    server.enqueue(MockResponse().setBody(post("p2", ""","isPendingReview":true""")))
    server.enqueue(MockResponse().setBody(post("p3", ""","isRemoved":true""")))

    val created = (1..3).map { CommunityCreatedPost.from(repository.createPost(null, "Salut")) }

    assertEquals(
      listOf(
        CommunityCreatedPost("p1", CommunityCreatedPost.Status.PUBLISHED),
        CommunityCreatedPost("p2", CommunityCreatedPost.Status.PENDING_REVIEW),
        CommunityCreatedPost("p3", CommunityCreatedPost.Status.REMOVED),
      ),
      created,
    )
    assertEquals(mapOf("id" to "p2", "status" to "pendingReview"), created[1].toMap())
    val request = server.takeRequest()
    assertEquals("/api/sdk/community/v1/posts", request.path)
    assertTrue(request.body.readUtf8().contains("\"body\":\"Salut\""))
  }

  private fun post(id: String, extra: String) =
    """{"id":"$id","groupId":"g1","groupName":"General","body":"Salut",""" +
      """"publishedAt":"2026-11-13T10:00:00.000Z","likeCount":0$extra}"""
}
