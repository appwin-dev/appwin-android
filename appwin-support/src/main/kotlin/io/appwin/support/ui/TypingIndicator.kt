package io.appwin.support.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Three bouncing dots - dashboard / iOS typing indicator parity. */
@Composable
internal fun TypingIndicator(label: String, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "typing")
  Row(
    modifier = modifier
      .fillMaxWidth()
      .semantics { contentDescription = label },
    horizontalArrangement = Arrangement.Start,
  ) {
    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(SupportTokens.surfaceMuted)
        .padding(horizontal = 12.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      repeat(3) { index ->
        val offset by transition.animateFloat(
          initialValue = 0f,
          targetValue = -3f,
          animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing, delayMillis = index * 120),
            repeatMode = RepeatMode.Reverse,
          ),
          label = "dot-$index",
        )
        Box(
          modifier = Modifier
            .size(6.dp)
            .graphicsLayer { translationY = offset }
            .clip(CircleShape)
            .background(SupportTokens.textTertiary),
        )
      }
    }
  }
}
