package io.appwin.community.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailRealtimeTest {

  private fun payload(raw: String) = Json.parseToJsonElement(raw)

  @Test
  fun `changes qui nomme le post ouvert declenche`() {
    val data = payload("""{"resourceId":"c1","orgId":"o","changes":["p1"]}""")
    assertTrue(PostDetailRealtime.touchesPost(data, "p1"))
  }

  @Test
  fun `changes qui nomme un autre post ne declenche pas`() {
    val data = payload("""{"resourceId":"c1","orgId":"o","changes":["p2"]}""")
    assertFalse(PostDetailRealtime.touchesPost(data, "p1"))
  }

  @Test
  fun `post updated reconnu par son resourceId`() {
    val data = payload("""{"resourceId":"p1","orgId":"o","changes":["pinned"]}""")
    assertTrue(PostDetailRealtime.touchesPost(data, "p1"))
  }

  @Test
  fun `sans changes (ancien serveur) ou illisible on rafraichit`() {
    assertTrue(PostDetailRealtime.touchesPost(payload("""{"resourceId":"c1","orgId":"o"}"""), "p1"))
    assertTrue(PostDetailRealtime.touchesPost(payload("""{"resourceId":"c1","changes":null}"""), "p1"))
    assertTrue(PostDetailRealtime.touchesPost(null, "p1"))
  }

  @Test
  fun `une rafale d evenements ne coute qu un rechargement`() = runTest {
    val payloads = Channel<Any?>(Channel.UNLIMITED)
    var signals = 0
    backgroundScope.launch {
      PostDetailRealtime.changesOf(payloads.receiveAsFlow(), "p1", debounceMillis = 700).collect { signals++ }
    }
    repeat(3) { payloads.send(payload("""{"resourceId":"c$it","changes":["p1"]}""")) }
    payloads.send(payload("""{"resourceId":"c9","changes":["p2"]}"""))
    advanceTimeBy(699)
    runCurrent()
    assertEquals(0, signals)
    advanceTimeBy(2)
    runCurrent()
    assertEquals(1, signals)

    payloads.send(payload("""{"resourceId":"p1"}"""))
    advanceTimeBy(701)
    runCurrent()
    assertEquals(2, signals)
  }
}
