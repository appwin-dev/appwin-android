package io.appwin.community.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.appwin.core.availability.AppwinPause

/**
 * The text of the "paused" screen: what the API served, and the SDK's own
 * string for whatever the server left empty.
 */
internal fun pausedCopy(pause: AppwinPause, strings: CommunityStrings): Pair<String, String> =
  pause.title.ifEmpty { strings.pausedTitle } to pause.message.ifEmpty { strings.pausedMessage }

/**
 * Shown in place of the feed while the organisation is on pause: the "coming
 * soon" illustration with the served title and message, never a diagnostic
 * card (a pause is not an integration error). Nothing to tap and nothing that
 * calls the server; the way out is the host's tab, or [onClose] full screen.
 */
@Composable
internal fun CommunityPausedScreen(pause: AppwinPause, onClose: (() -> Unit)? = null) {
  val context = LocalContext.current
  val strings = remember(context) { communityStrings(context) }
  val (title, message) = remember(strings, pause) { pausedCopy(pause, strings) }
  val entrance = remember { Animatable(0f) }
  LaunchedEffect(Unit) { entrance.animateTo(1f, tween(durationMillis = 350, easing = EaseOut)) }

  Box(Modifier.fillMaxSize().background(CommunityColors.background).safeDrawingPadding()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .heightIn(min = maxHeight),
      ) {
        Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
        UnavailableContent(title, message) { entrance.value }
        Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
      }
    }
    if (onClose != null) {
      GlassIconButton(
        Icons.Default.Close,
        contentDescription = strings.close,
        onClick = onClose,
        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
      )
    }
  }
}
