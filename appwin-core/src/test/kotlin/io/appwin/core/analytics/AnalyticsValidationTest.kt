package io.appwin.core.analytics

import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsValidationTest {
  @Test
  fun `les events automatiques du cycle de vie et du push sont reserves`() {
    listOf("app_open", "app_background", "push_opt_in", "push_opened").forEach {
      assertTrue("$it must be reserved", it in AnalyticsValidation.RESERVED_NAMES)
    }
  }
}
