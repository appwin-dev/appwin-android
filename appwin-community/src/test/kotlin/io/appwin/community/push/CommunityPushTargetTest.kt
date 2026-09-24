package io.appwin.community.push

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
    assertEquals(CommunityPushTarget("p1"), CommunityPushTarget.from("appwin://community/post/p1"))
    assertEquals(
      CommunityPushTarget("p1", "c9"),
      CommunityPushTarget.from("appwin://community/post/p1/thread/c9"),
    )
    assertEquals(CommunityPushTarget("p1"), CommunityPushTarget.from("appwin:///community/post/p1"))
  }

  @Test
  fun `other routes carry no target`() {
    assertNull(CommunityPushTarget.from("appwin://community"))
    assertNull(CommunityPushTarget.from("appwin://support/conversation/c1"))
    assertNull(CommunityPushTarget.from("https://example.com/community/post/p1"))
    assertNull(CommunityPushTarget.from(null))
  }
}
