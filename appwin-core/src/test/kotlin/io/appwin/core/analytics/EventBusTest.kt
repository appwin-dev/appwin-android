package io.appwin.core.analytics

import io.appwin.core.consent.ConsentPurpose
import org.junit.Assert.assertEquals
import org.junit.Test

class EventBusTest {
  @Test
  fun `une finalite refusee saute son abonne, sans finalite on entend toujours`() {
    val bus = EventBus { it == ConsentPurpose.ANALYTICS }
    val seen = mutableListOf<String>()
    val ads = bus.subscribe(ConsentPurpose.ADVERTISING) { seen += "ads:${it.name}" }
    val any = bus.subscribe(null) { seen += "any:${it.name}" }
    val crash = bus.subscribe(ConsentPurpose.ANALYTICS) { seen += "crash:${it.name}" }
    bus.publish(EventBus.Event("purchase", "e1", null))
    assertEquals(listOf("any:purchase", "crash:purchase"), seen.sorted())
    ads.close(); any.close(); crash.close()
  }

  @Test
  fun `la finalite est relue a chaque event`() {
    var granted = true
    val bus = EventBus { granted }
    val seen = mutableListOf<String>()
    bus.subscribe(ConsentPurpose.ADVERTISING) { seen += it.eventId }
    bus.publish(EventBus.Event("a", "1", null))
    granted = false
    bus.publish(EventBus.Event("a", "2", null))
    granted = true
    bus.publish(EventBus.Event("a", "3", null))
    assertEquals(listOf("1", "3"), seen)
  }

  @Test
  fun `fermer le jeton desabonne et un abonne qui plante n empeche pas les autres`() {
    val bus = EventBus { true }
    val seen = mutableListOf<String>()
    bus.subscribe(null) { error("boom") }
    val token = bus.subscribe(null) { seen += "closed" }
    bus.subscribe(null) { seen += "kept" }
    token.close()
    bus.publish(EventBus.Event("a", "1", null))
    assertEquals(listOf("kept"), seen)
  }
}
