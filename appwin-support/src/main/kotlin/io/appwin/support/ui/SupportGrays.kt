package io.appwin.support.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.appwin.support.domain.MessengerGrayWarmth

/**
 * Gray scales for the `grayWarmth` knob, same table as the web widget
 * (sdk/appwin-web/src/widget/ui/grays.ts) and iOS `AppwinGrays`. Figma
 * Tokens-color mapping: page=50/950 (bg/page), surface=white/900
 * (bg/container), raised=100/800 (bg/low), border=200/700, text=900/white,
 * muted=700/400, subtle=400/500.
 */
@Immutable
internal data class SupportGrays(
  val page: Color,
  val surface: Color,
  val raised: Color,
  val text: Color,
  val muted: Color,
  val subtle: Color,
  val border: Color,
)

internal fun supportGrays(warmth: MessengerGrayWarmth, dark: Boolean): SupportGrays {
  // Columns: page, surface, raised, text, muted, subtle, border.
  val row: LongArray = when (warmth) {
    MessengerGrayWarmth.SLATE ->
      if (dark) longArrayOf(0x020617, 0x0F172A, 0x1E293B, 0xFFFFFF, 0x94A3B8, 0x64748B, 0x334155)
      else longArrayOf(0xF8FAFC, 0xFFFFFF, 0xF1F5F9, 0x0F172A, 0x334155, 0x94A3B8, 0xE2E8F0)
    MessengerGrayWarmth.GRAY ->
      if (dark) longArrayOf(0x030712, 0x111827, 0x1F2937, 0xFFFFFF, 0x9CA3AF, 0x6B7280, 0x374151)
      else longArrayOf(0xF9FAFB, 0xFFFFFF, 0xF3F4F6, 0x111827, 0x374151, 0x9CA3AF, 0xE5E7EB)
    MessengerGrayWarmth.ZINC ->
      if (dark) longArrayOf(0x09090B, 0x18181B, 0x27272A, 0xFFFFFF, 0xA1A1AA, 0x71717A, 0x3F3F46)
      else longArrayOf(0xFAFAFA, 0xFFFFFF, 0xF4F4F5, 0x18181B, 0x3F3F46, 0xA1A1AA, 0xE4E4E7)
    MessengerGrayWarmth.NEUTRAL ->
      if (dark) longArrayOf(0x0A0A0A, 0x171717, 0x262626, 0xFFFFFF, 0xA3A3A3, 0x737373, 0x404040)
      else longArrayOf(0xFAFAFA, 0xFFFFFF, 0xF5F5F5, 0x171717, 0x404040, 0xA3A3A3, 0xE5E5E5)
    MessengerGrayWarmth.STONE ->
      if (dark) longArrayOf(0x0C0A09, 0x1C1917, 0x292524, 0xFFFFFF, 0xA8A29E, 0x78716C, 0x44403C)
      else longArrayOf(0xFAFAF9, 0xFFFFFF, 0xF5F5F4, 0x1C1917, 0x44403C, 0xA8A29E, 0xE7E5E4)
  }
  fun c(i: Int) = Color(0xFF000000 or row[i])
  return SupportGrays(c(0), c(1), c(2), c(3), c(4), c(5), c(6))
}

/** Provided by [SupportTheme] from the config; read through [SupportTokens]. */
internal val LocalSupportGrays = staticCompositionLocalOf {
  supportGrays(MessengerGrayWarmth.SLATE, dark = false)
}
