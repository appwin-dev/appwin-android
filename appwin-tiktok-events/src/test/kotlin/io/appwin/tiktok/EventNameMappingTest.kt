package io.appwin.tiktok

import org.junit.Assert.assertEquals
import org.junit.Test

class EventNameMappingTest {
  @Test
  fun mapsKnownNamesToTikTokStandardEvents() {
    assertEquals("Purchase", mapEventName("purchase"))
    assertEquals("StartTrial", mapEventName("start_trial"))
    assertEquals("Subscribe", mapEventName("subscribe"))
    assertEquals("CompleteRegistration", mapEventName("complete_registration"))
  }

  @Test
  fun forwardsUnknownNamesAsCustomEvents() {
    assertEquals("level_completed", mapEventName("level_completed"))
  }
}
