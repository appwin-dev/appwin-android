package io.appwin.core.analytics

import io.appwin.core.consent.ConsentPurpose
import io.appwin.core.consent.ConsentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsRuntimeWiringTest {
  private fun runtime() = AnalyticsRuntime(CoroutineScope(Job()), client = { null }, reauthorize = { true })

  @Test
  fun `les regies suivent advertising, les crashs suivent analytics`() {
    val runtime = runtime()
    val ads = mutableListOf<String>()
    val crashes = mutableListOf<String>()
    runtime.wireAdSignals { name, _, _ -> ads += name }
    runtime.wireCrashBreadcrumbs { crashes += it }

    runtime.setConsent(ConsentState.DENIED, ConsentPurpose.ADVERTISING)
    runtime.eventBus.publish(EventBus.Event("one", "e1", null))
    assertEquals(emptyList<String>(), ads)
    assertEquals(listOf("one"), crashes)

    runtime.setConsent(ConsentState.GRANTED, ConsentPurpose.ADVERTISING)
    runtime.setConsent(ConsentState.DENIED, ConsentPurpose.ANALYTICS)
    runtime.eventBus.publish(EventBus.Event("two", "e2", null))
    assertEquals(listOf("two"), ads)
    assertEquals(listOf("one"), crashes)
  }
}
