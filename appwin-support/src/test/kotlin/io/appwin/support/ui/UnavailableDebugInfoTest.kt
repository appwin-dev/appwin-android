package io.appwin.support.ui

import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnavailableDebugInfoTest {
  @Test
  fun `each verdict is named as the SDK logs it`() {
    assertEquals("notInitialized", unavailableDebugInfo(null)?.code)
    assertEquals("notConfigured", unavailableDebugInfo(AppwinInitResult.NotConfigured)?.code)
    assertEquals("unknown", unavailableDebugInfo(AppwinInitResult.Unknown)?.code)
    assertEquals(
      "unavailable(plan)",
      unavailableDebugInfo(AppwinInitResult.unavailable(AppwinUnavailableReason.PLAN))?.code,
    )
    assertEquals(
      "unavailable(disabled)",
      unavailableDebugInfo(AppwinInitResult.unavailable(AppwinUnavailableReason.DISABLED))?.code,
    )
  }

  @Test
  fun `the action fixes the reason where it can be fixed`() {
    assertEquals(
      DASHBOARD_URL,
      unavailableDebugInfo(AppwinInitResult.unavailable(AppwinUnavailableReason.DISABLED))?.actionUrl,
    )
    assertEquals(
      PRICING_URL,
      unavailableDebugInfo(AppwinInitResult.unavailable(AppwinUnavailableReason.PLAN))?.actionUrl,
    )
    assertEquals(SUPPORT_DOCS_URL, unavailableDebugInfo(AppwinInitResult.NotConfigured)?.actionUrl)
    assertEquals(SUPPORT_DOCS_URL, unavailableDebugInfo(null)?.actionUrl)
  }

  @Test
  fun `a ready verdict has nothing to explain`() {
    assertNull(unavailableDebugInfo(AppwinInitResult.Ready))
  }
}
