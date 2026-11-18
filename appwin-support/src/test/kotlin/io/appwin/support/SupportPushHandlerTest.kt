package io.appwin.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SupportPushHandlerTest {
  @Test
  fun `conversation id is read from the support route`() {
    assertEquals("c-42", SupportPushHandler.conversationId("appwin://support/conversation/c-42"))
    assertEquals("c-42", SupportPushHandler.conversationId("appwin:///support/conversation/c-42"))
  }

  @Test
  fun `other routes carry no conversation`() {
    assertNull(SupportPushHandler.conversationId(null))
    assertNull(SupportPushHandler.conversationId("appwin://support"))
    assertNull(SupportPushHandler.conversationId("appwin://community/post/p-1"))
    assertNull(SupportPushHandler.conversationId("https://example.com/conversation/c-42"))
  }
}
