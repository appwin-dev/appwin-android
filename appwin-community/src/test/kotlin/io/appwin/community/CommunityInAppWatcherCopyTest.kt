package io.appwin.community

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.appwin.community.domain.CommunityNotification
import io.appwin.community.domain.CommunityNotificationType
import io.appwin.community.ui.communityStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommunityInAppWatcherCopyTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val strings = communityStrings(context)

  @Test
  fun `comment with excerpt mirrors push copy shape`() {
    val (title, body) = CommunityInAppWatcher.bannerCopy(
      notification(
        type = CommunityNotificationType.POST_COMMENT,
        nickname = "Alex",
        excerpt = "nice post",
      ),
      strings,
    )
    assertEquals(strings.notifTitleComment, title)
    assertEquals(strings.notifBodyCommentedExcerpt("Alex", "nice post"), body)
  }

  @Test
  fun `reaction without excerpt uses fallback body`() {
    val (title, body) = CommunityInAppWatcher.bannerCopy(
      notification(
        type = CommunityNotificationType.POST_REACTION,
        nickname = "Sam",
        excerpt = null,
      ),
      strings,
    )
    assertEquals(strings.notifTitleReaction, title)
    assertEquals(strings.notifBodyLiked("Sam"), body)
  }

  @Test
  fun `blank actor falls back to someone`() {
    val (_, body) = CommunityInAppWatcher.bannerCopy(
      notification(
        type = CommunityNotificationType.POLL_VOTE,
        nickname = "  ",
        excerpt = null,
      ),
      strings,
    )
    assertTrue(body.contains(strings.notifSomeone))
  }

  private fun notification(
    type: CommunityNotificationType,
    nickname: String?,
    excerpt: String?,
  ) = CommunityNotification(
    id = "n1",
    type = type,
    actorNickname = nickname,
    targetType = "post",
    targetId = "t1",
    postId = "p1",
    excerpt = excerpt,
    createdAtMillis = 1L,
  )
}
