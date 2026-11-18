package io.appwin.core.analytics

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Uuid7Test {

  @Test
  fun `le format est un uuid minuscule 8-4-4-4-12 avec version 7 et variant 10`() {
    val uuid = Uuid7.generate()
    assertTrue(uuid.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")))
  }

  @Test
  fun `le timestamp en millisecondes est embarque dans les 48 premiers bits`() {
    val nowMs = 1_788_500_000_123L
    val uuid = Uuid7.generate(nowMs = nowMs, random = Random(42))
    assertEquals(nowMs, Uuid7.timestampMs(uuid))
  }

  @Test
  fun `deux generations a des instants croissants trient lexicalement`() {
    val a = Uuid7.generate(nowMs = 1_000L, random = Random(1))
    val b = Uuid7.generate(nowMs = 2_000L, random = Random(1))
    assertTrue(a < b)
  }

  @Test
  fun `dix mille generations sont uniques`() {
    val seen = HashSet<String>(20_000)
    repeat(10_000) { seen.add(Uuid7.generate()) }
    assertEquals(10_000, seen.size)
  }

  @Test
  fun `timestampMs rejette une chaine qui n'est pas un uuid`() {
    assertNull(Uuid7.timestampMs("pas-un-uuid"))
    assertNull(Uuid7.timestampMs("zz".repeat(16)))
  }
}
