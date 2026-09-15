package io.appwin.support

import io.appwin.support.data.IsoDate
import org.junit.Assert.assertEquals
import org.junit.Test

class IsoDateTest {
  @Test
  fun `Z and offset forms resolve to the same UTC epoch`() {
    val utc = IsoDate.toMillis("2026-09-09T13:26:00Z")
    assertEquals(utc, IsoDate.toMillis("2026-09-09T13:26:00.000Z"))
    assertEquals(utc, IsoDate.toMillis("2026-09-09T15:26:00+02:00"))
    assertEquals(utc, IsoDate.toMillis("2026-09-09T13:26:00+00:00"))
  }

  @Test
  fun `unreadable input yields zero`() {
    assertEquals(0L, IsoDate.toMillis(null))
    assertEquals(0L, IsoDate.toMillis("not-a-date"))
  }
}
