package io.appwin.analytics

import io.appwin.core.consent.ConsentState
import org.junit.Test

class AppwinAnalyticsTest {
  // The façade delegates to Core, whose pipeline has its own suite. What this
  // module guarantees is the product contract: calling it before `configure`
  // must be a silent no-op, never a crash.
  @Test
  fun `les appels avant configure ne crashent jamais`() {
    AppwinAnalytics.track("some_event", mapOf("plan" to "pro"))
    AppwinAnalytics.screen("home")
    AppwinAnalytics.flush()
    AppwinAnalytics.setConsent(ConsentState.GRANTED)
    AppwinAnalytics.recordError(IllegalStateException("caught"))
  }

  @Test
  @Suppress("DEPRECATION")
  fun `AnalyticsConsent compile encore comme alias de ConsentState`() {
    val old: AnalyticsConsent = ConsentState.DENIED
    org.junit.Assert.assertEquals(AnalyticsConsent.DENIED, old)
  }
}
