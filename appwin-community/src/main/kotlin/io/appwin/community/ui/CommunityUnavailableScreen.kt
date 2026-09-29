package io.appwin.community.ui

import android.content.pm.ApplicationInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityConfig
import io.appwin.core.availability.AppwinInitResult

private val TitleColor = Color(0xFF0E172A)
private val MessageColor = Color(0xFF64758B)
private val SkeletonStrong = Color(0xFFE2E8F0)
private val CardShadow = Color(0x0F020617)

/**
 * Shown in place of the feed while Community is not ready.
 *
 * Built on the palette constants, not the studio theme: no configuration has
 * been loaded when Community is closed.
 */
@Composable
internal fun CommunityUnavailableScreen(result: AppwinInitResult?) {
  val context = LocalContext.current
  val strings = remember(context) { communityStrings(context) }
  // The host app's flag: the library AAR is always a release build.
  val debugInfo = remember(context, result) {
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (debuggable) unavailableDebugInfo(result) else null
  }
  val entrance = remember { Animatable(0f) }
  LaunchedEffect(Unit) { entrance.animateTo(1f, tween(durationMillis = 350, easing = EaseOut)) }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(CommunityColors.background)
      .safeDrawingPadding(),
  ) {
    // Centred while it fits, scrollable when the debug alert makes it taller
    // than a small screen.
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .heightIn(min = maxHeight),
    ) {
      debugInfo?.let {
        CommunityDebugAlert(it, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
      }
      Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
      UnavailableContent(strings.unavailableTitle, strings.unavailableMessage) { entrance.value }
      Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
    }
  }
}

@Composable
private fun UnavailableContent(title: String, message: String, progress: () -> Float) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .padding(horizontal = 24.dp)
      .widthIn(max = 340.dp)
      .fillMaxWidth()
      .graphicsLayer {
        // Read in the layer block so the entrance does not recompose each frame.
        val p = progress()
        alpha = p
        translationY = (1f - p) * 8.dp.toPx()
      },
  ) {
    UnavailableIllustration()
    Spacer(Modifier.height(32.dp))
    Text(
      text = title,
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
      color = TitleColor,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = message,
      fontSize = 15.sp,
      lineHeight = 21.sp,
      color = MessageColor,
      textAlign = TextAlign.Center,
    )
  }
}

/**
 * A feed still being set up: two blank posts, fanned out, under the community
 * badge. Reads as "a space is coming" rather than "an error".
 */
@Composable
private fun UnavailableIllustration() {
  val brand = CommunityColors.brand
  val fill = remember { accentComposeBrush(CommunityConfig()) }
  Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 220.dp, height = 200.dp)) {
    Box(
      modifier = Modifier
        .size(210.dp)
        .background(brand.copy(alpha = 0.07f), CircleShape),
    )
    PlaceholderPost(
      lines = listOf(96.dp, 72.dp),
      modifier = Modifier.offset(x = (-22).dp, y = (-18).dp).rotate(-7f),
    )
    PlaceholderPost(
      lines = listOf(112.dp, 60.dp),
      modifier = Modifier.offset(x = 20.dp, y = 12.dp).rotate(5f),
    )
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .offset(y = 62.dp)
        .shadow(12.dp, CircleShape, ambientColor = brand, spotColor = brand)
        .size(56.dp)
        .background(fill, CircleShape)
        .border(4.dp, Color.White, CircleShape),
    ) {
      Icon(
        imageVector = CommunityIcons.Conversation,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Composable
private fun PlaceholderPost(lines: List<Dp>, modifier: Modifier = Modifier) {
  Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier
      .width(150.dp)
      .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = CardShadow, spotColor = CardShadow)
      .background(CommunityColors.surface, RoundedCornerShape(16.dp))
      .border(1.dp, CommunityColors.border, RoundedCornerShape(16.dp))
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.size(24.dp).background(SkeletonStrong, CircleShape))
      Box(Modifier.size(width = 56.dp, height = 8.dp).background(SkeletonStrong, CircleShape))
    }
    lines.forEach { width ->
      Box(Modifier.size(width = width, height = 8.dp).background(CommunityColors.surfaceMuted, CircleShape))
    }
  }
}
