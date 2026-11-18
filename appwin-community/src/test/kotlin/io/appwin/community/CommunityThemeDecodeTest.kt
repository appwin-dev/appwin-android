package io.appwin.community

import io.appwin.community.data.CommunityThemeDto
import io.appwin.community.domain.CommunityColorScheme
import io.appwin.community.domain.CommunityGrayWarmth
import io.appwin.community.ui.gradientTintHex
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityThemeDecodeTest {
  // Same settings as the API client and the config cache.
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

  private fun theme(raw: String) = json.decodeFromString(CommunityThemeDto.serializer(), raw).toDomain()

  @Test
  fun `theme reads colour scheme and gray warmth`() {
    val themed = theme("""{"colorScheme":"dark","grayWarmth":"stone"}""")
    assertEquals(CommunityColorScheme.DARK, themed.colorScheme)
    assertEquals(CommunityGrayWarmth.STONE, themed.grayWarmth)
  }

  @Test
  fun `a theme from before these fields stays light slate`() {
    val legacy = theme("""{"primary":"#123456","radius":"low"}""")
    assertEquals(CommunityColorScheme.LIGHT, legacy.colorScheme)
    assertEquals(CommunityGrayWarmth.SLATE, legacy.grayWarmth)
  }

  @Test
  fun `unknown values from a newer server fall back instead of failing`() {
    val unknown = theme("""{"colorScheme":"sepia","grayWarmth":"olive"}""")
    assertEquals(CommunityColorScheme.LIGHT, unknown.colorScheme)
    assertEquals(CommunityGrayWarmth.SLATE, unknown.grayWarmth)
  }

  @Test
  fun `default gradient stop is the dashboard's lighter tint`() {
    assertEquals("#FDD3FF", gradientTintHex("#F2A6F6"))
    assertEquals("#C6C6C6", gradientTintHex("#808080"))
    assertEquals("nope", gradientTintHex("nope"))
  }
}
