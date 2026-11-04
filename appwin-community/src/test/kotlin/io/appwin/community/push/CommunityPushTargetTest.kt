package io.appwin.community.push

import io.appwin.community.AppwinCommunityPostTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommunityPushTargetTest {
  @Test
  fun `post and replies thread are read from the community route`() {
    assertEquals(AppwinCommunityPostTarget("p1"), communityPostTarget("appwin://community/post/p1"))
    assertEquals(
      AppwinCommunityPostTarget("p1", "c9"),
      communityPostTarget("appwin://community/post/p1/thread/c9"),
    )
    assertEquals(AppwinCommunityPostTarget("p1"), communityPostTarget("appwin:///community/post/p1"))
  }

  @Test
  fun `other routes carry no target`() {
    assertNull(communityPostTarget("appwin://community"))
    assertNull(communityPostTarget("appwin://support/conversation/c1"))
    assertNull(communityPostTarget("https://example.com/community/post/p1"))
    assertNull(communityPostTarget(null))
  }
}
