package io.appwin.community.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Back from a post opened in the inbox, the list is not reloaded from page one. */
class NotificationsInboxStateTest {
  @Test
  fun `loads once per visit, again only on retry`() {
    val inbox = NotificationsInboxState()
    assertTrue(inbox.claimLoad())
    // The screen comes back into composition after the post: same state.
    assertFalse(inbox.claimLoad())
    inbox.attempt++
    assertTrue(inbox.claimLoad())
    assertFalse(inbox.claimLoad())
  }

  @Test
  fun `a new visit from the feed starts fresh`() {
    val first = NotificationsInboxState()
    first.claimLoad()
    assertTrue(NotificationsInboxState().claimLoad())
  }
}
