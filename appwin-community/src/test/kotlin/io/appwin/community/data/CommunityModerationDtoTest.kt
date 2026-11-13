package io.appwin.community.data

import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.ModerationAction
import io.appwin.community.domain.ModerationContentStatus
import io.appwin.community.domain.ModerationQueueItem
import io.appwin.community.domain.ModerationTargetType
import io.appwin.core.network.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityModerationDtoTest {
  private val json = ApiClient.json

  @Test
  fun `a decision decodes, the classifier has no name`() {
    val raw = """{"action":"hide_content","actorName":null,"automated":true,"at":"2026-10-19T08:00:00.000Z"}"""
    val decision = json.decodeFromString(ModerationQueueDto.DecisionDto.serializer(), raw).toDomain()
    assertNotNull(decision)
    assertEquals("hide_content", decision!!.action)
    assertNull(decision.actorName)
    assertTrue(decision.automated)
  }

  @Test
  fun `the contact only comes for moderators`() {
    val plain = json.decodeFromString(MemberContactDto.serializer(), """{"id":"p1","nickname":"Alice"}""").toDomain()
    assertTrue(plain.isEmpty)
    val moderated = json.decodeFromString(
      MemberContactDto.serializer(),
      """{"id":"p1","email":"a@b.co","externalId":"u-42"}""",
    ).toDomain()
    assertEquals("a@b.co", moderated.email)
    assertEquals("u-42", moderated.externalId)
    assertFalse(moderated.isEmpty)
  }

  @Test
  fun `deciding from the handled tab updates the status and the actor`() {
    val post = json.decodeFromString(CommunityPostDto.serializer(), """{"id":"post-1"}""").toDomain()
    val item = item(post, ModerationContentStatus.PENDING)
    val restored = item.decided(ModerationAction.RESTORE_CONTENT, "Maeva", atMillis = 42)
    assertEquals(ModerationContentStatus.PUBLISHED, restored.status)
    assertEquals("Maeva", restored.decision?.actorName)
    assertEquals(42L, restored.decision?.atMillis)
    assertEquals(ModerationContentStatus.REMOVED, item.decided(ModerationAction.REMOVE_CONTENT, "M", 0).status)
  }

  private fun item(post: CommunityPost, status: ModerationContentStatus) = ModerationQueueItem(
    targetType = ModerationTargetType.POST,
    targetId = post.id,
    status = status,
    aiCategories = emptyList(),
    reports = emptyList(),
    post = post,
    comment = null,
  )
}
