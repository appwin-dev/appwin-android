package io.appwin.support.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.core.availability.AppwinPause

/**
 * The text of the "paused" screen: what the API served, and the SDK's own
 * string for whatever the server left empty.
 */
internal fun pausedCopy(pause: AppwinPause, strings: SupportStrings): Pair<String, String> =
  pause.title.ifEmpty { strings.pausedTitle } to pause.message.ifEmpty { strings.pausedMessage }

/**
 * The messenger of an organisation on pause: a title and a message, nothing to
 * tap and nothing that calls the server. The way out is the container's
 * (the tab, or the sheet's close button).
 */
@Composable
internal fun SupportPausedContent(pause: AppwinPause, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val (title, message) = remember(context, pause) { pausedCopy(pause, SupportStrings(context)) }
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 32.dp, vertical = 48.dp),
  ) {
    Text(
      text = title,
      fontSize = 20.sp,
      fontWeight = FontWeight.SemiBold,
      color = SupportTokens.textMain,
      textAlign = TextAlign.Center,
    )
    Text(
      text = message,
      fontSize = 14.sp,
      color = SupportTokens.textSecondary,
      textAlign = TextAlign.Center,
    )
  }
}
