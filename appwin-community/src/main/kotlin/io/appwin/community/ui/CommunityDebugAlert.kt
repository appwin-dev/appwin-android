package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarningHigh = Color(0xFFFCD34D)
private val WarningSolid = Color(0xFFD97706)
private val DebugAmber = Color(0xFFF5A623)
private val DebugAmberText = Color(0xFFB45309)
private val CodeColor = Color(0xFF64758B)
private val ShadowS = Color(0x0D020617)
private val ShadowM = Color(0x1A020617)

/**
 * The dashboard's `SetupBanner` (Figma `alert-banner/large`, 3518:64770),
 * stacked for phone width: the same alert a studio already reads in the SaaS.
 * Only composed for debuggable hosts.
 */
@Composable
internal fun CommunityDebugAlert(info: UnavailableDebugInfo, modifier: Modifier = Modifier) {
  val uriHandler = LocalUriHandler.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(Brush.horizontalGradient(listOf(WarningHigh, Color.White)))
      .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
  ) {
    Icon(
      imageVector = DangerCircle,
      contentDescription = null,
      tint = WarningSolid,
      modifier = Modifier.size(20.dp),
    )
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .weight(1f)
        .shadow(20.dp, RoundedCornerShape(16.dp), ambientColor = ShadowS, spotColor = ShadowS)
        .background(Color.White, RoundedCornerShape(16.dp))
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "DEBUG",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.4.sp,
          color = DebugAmberText,
          modifier = Modifier
            .background(DebugAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
          text = info.code,
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          color = CodeColor,
          maxLines = 1,
        )
      }
      Text(
        text = info.message,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 17.sp,
        color = WarningSolid,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
          .align(Alignment.End)
          .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = ShadowM, spotColor = ShadowM)
          .clip(RoundedCornerShape(12.dp))
          .background(Brush.linearGradient(listOf(Color(0xFF9B3412), CommunityColors.brand)))
          .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
          .clickable { runCatching { uriHandler.openUri(info.actionUrl) } }
          .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        Icon(
          Icons.Default.ArrowOutward,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(14.dp),
        )
        Text(text = info.actionLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
      }
    }
  }
}

/** Solar `Danger Circle` (Bold), the dashboard's alert glyph. */
private val DangerCircle: ImageVector by lazy {
  ImageVector.Builder(
    name = "AppwinDangerCircle",
    defaultWidth = 20.dp,
    defaultHeight = 20.dp,
    viewportWidth = 20f,
    viewportHeight = 20f,
  ).apply {
    addPath(
      pathData = PathParser().parsePathString(
        "M18.3333 10C18.3333 5.39763 14.6024 1.66667 10 1.66667C5.39763 1.66667 1.66667 5.39763 " +
          "1.66667 10C1.66667 14.6024 5.39763 18.3333 10 18.3333C14.6024 18.3333 18.3333 14.6024 " +
          "18.3333 10ZM10 5.20833C10.3452 5.20833 10.625 5.48816 10.625 5.83333V10.8333C10.625 " +
          "11.1785 10.3452 11.4583 10 11.4583C9.65482 11.4583 9.375 11.1785 9.375 10.8333V5.83333C" +
          "9.375 5.48816 9.65482 5.20833 10 5.20833ZM10 14.1667C10.4602 14.1667 10.8333 13.7936 " +
          "10.8333 13.3333C10.8333 12.8731 10.4602 12.5 10 12.5C9.53976 12.5 9.16667 12.8731 " +
          "9.16667 13.3333C9.16667 13.7936 9.53976 14.1667 10 14.1667Z",
      ).toNodes(),
      fill = SolidColor(Color.Black),
      pathFillType = PathFillType.EvenOdd,
    )
  }.build()
}
