package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityNotification
import io.appwin.community.domain.CommunityNotificationType
import io.appwin.community.domain.CommunityProfile
import java.text.DateFormat
import java.util.Date

/**
 * Figma « Notification » (174:1396): one sanction, what happened above, the
 * card with its label and « J'ai compris ». Shown at the top of the inbox
 * until acknowledged.
 */
@Composable
internal fun SanctionCard(
  sanction: CommunityNotification,
  teamName: String,
  author: CommunityProfile?,
  strings: CommunityStrings,
  onUnderstood: () -> Unit,
) {
  val reason = sanction.reason?.trim()?.takeIf { it.isNotEmpty() }
  // The moderator's own words; a reason key is shown as the label instead.
  val message = reason?.takeIf { strings.reasonLabel(it) == it }
  val headline = when (sanction.type) {
    CommunityNotificationType.ACCOUNT_WARNED -> strings.warnSection(teamName)
    CommunityNotificationType.ACCOUNT_BANNED ->
      if (sanction.sanctionUntilMillis == null) strings.banSection else strings.tempBanSection
    else -> if (sanction.targetType == "comment") strings.commentRemovedSection else strings.postRemovedSection
  }
  val label = when (sanction.type) {
    CommunityNotificationType.ACCOUNT_WARNED -> strings.warnTitle
    CommunityNotificationType.ACCOUNT_BANNED -> sanction.sanctionUntilMillis?.let {
      strings.tempBanLabel(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)))
    } ?: strings.permanentBanLabel
    else -> if (reason == null || message != null) strings.removedFallbackReason else strings.reasonLabel(reason)
  }
  val warned = sanction.type == CommunityNotificationType.ACCOUNT_WARNED
  val labelColor = if (warned) CommunityColors.caution else CommunityColors.alert

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CommunityColors.textPrimary)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(CommunityColors.surface)
        .border(1.dp, CommunityColors.surfaceMuted, RoundedCornerShape(16.dp)),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (sanction.type) {
          CommunityNotificationType.CONTENT_REMOVED -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CommunityAvatar(author?.avatarUrl, author?.nickname.orEmpty(), size = 24)
              Text(author?.nickname.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CommunityColors.textPrimary)
              Text(relativeTime(sanction.createdAtMillis, strings), fontSize = 10.sp, color = CommunityColors.textTertiary)
            }
            sanction.excerpt?.takeIf { it.isNotBlank() }?.let { BodyText(it) }
          }
          CommunityNotificationType.ACCOUNT_BANNED -> BodyText(message ?: strings.banDefaultBody)
          else -> BodyText(message.orEmpty())
        }
      }
      Box(Modifier.fillMaxWidth().height(1.dp).background(CommunityColors.surfaceMuted))
      Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          if (warned) SolarModerationIcons.DangerTriangle else SolarModerationIcons.Forbidden,
          contentDescription = null,
          tint = labelColor,
          modifier = Modifier.size(14.dp),
        )
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = labelColor, maxLines = 2, modifier = Modifier.weight(1f))
        CommunityActionPill(title = strings.understood, tone = PillTone.INVERT, onClick = onUnderstood)
      }
    }
  }
}

@Composable
private fun BodyText(text: String) {
  Text(text, fontSize = 14.sp, lineHeight = 18.sp, color = CommunityColors.textPrimary, modifier = Modifier.fillMaxWidth())
}
