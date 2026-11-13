package io.appwin.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPermissionGrantTest {
  @Test
  fun `un accord quand les notifications etaient deja actives ne previent pas Core`() {
    assertFalse(PushPermissionGrant.shouldSignalCore(enabledBeforeRequest = true, granted = true))
  }

  @Test
  fun `un accord quand les notifications etaient coupees previent Core`() {
    assertTrue(PushPermissionGrant.shouldSignalCore(enabledBeforeRequest = false, granted = true))
  }

  @Test
  fun `un refus ne previent jamais Core`() {
    assertFalse(PushPermissionGrant.shouldSignalCore(enabledBeforeRequest = false, granted = false))
  }
}
