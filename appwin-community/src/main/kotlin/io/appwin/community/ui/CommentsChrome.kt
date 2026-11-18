package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma "Afficher 2 réponses": a short rule, the label, a chevron. */
@Composable
internal fun ShowRepliesLink(label: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.padding(start = 32.dp).clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Box(modifier = Modifier.size(width = 20.dp, height = 1.dp).background(CommunityColors.border))
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = CommunityColors.textTertiary,
    )
    Icon(
      Icons.Default.KeyboardArrowDown,
      contentDescription = null,
      tint = CommunityColors.textTertiary,
      modifier = Modifier.size(12.dp),
    )
  }
}

/** Figma 29:5471: chat glyph, "Aucun commentaire pour l'instant", "Donne ton avis !". */
@Composable
internal fun CommentsEmptyState(strings: CommunityStrings) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 80.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      SolarIcons.ChatLineDuotone,
      contentDescription = null,
      tint = CommunityColors.textTertiary,
      modifier = Modifier.padding(bottom = 12.dp).size(32.dp),
    )
    Text(
      text = strings.noComments,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textPrimary,
      textAlign = TextAlign.Center,
    )
    Text(
      text = strings.noCommentsHint,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textTertiary,
      textAlign = TextAlign.Center,
    )
  }
}

/** Content removed by moderation as it was written: said plainly, the reason waits behind the bell. */
@Composable
internal fun RemovedByModerationAlert(title: String, strings: CommunityStrings, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(strings.postRemovedAlertMessage) },
    confirmButton = { TextButton(onClick = onDismiss) { Text(strings.understood) } },
  )
}
