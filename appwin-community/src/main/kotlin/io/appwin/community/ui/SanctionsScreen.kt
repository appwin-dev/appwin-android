package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.domain.CommunityNotification
import io.appwin.community.domain.CommunityNotificationType
import io.appwin.community.domain.CommunityProfile
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Figma « Notification » (174:1396): what moderation did to the member, one
 * card per sanction until they tap « J'ai compris ».
 */
@Composable
internal fun SanctionsScreen(viewModel: CommunityViewModel, strings: CommunityStrings, onBack: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var sanctions by remember { mutableStateOf<List<CommunityNotification>?>(null) }
  LaunchedEffect(Unit) {
    val unread = runCatching { viewModel.moderation.sanctions() }.getOrDefault(emptyList()).filterNot { it.isRead }
    sanctions = unread
    viewModel.setUnreadSanctionCount(unread.size)
  }

  Column(modifier = Modifier.fillMaxSize().background(CommunityColors.background)) {
    TextNavBar(
      title = strings.notificationTitle,
      leadingLabel = strings.back,
      onLeading = onBack,
      containerColor = CommunityColors.background,
    )
    val list = sanctions
    if (list == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
      ) {
        items(list, key = { it.id }) { sanction ->
          SanctionCard(sanction, state.config.context.projectName, state.profile, strings) {
            val rest = list.filterNot { it.id == sanction.id }
            sanctions = rest
            viewModel.setUnreadSanctionCount(rest.size)
            scope.launch { runCatching { viewModel.moderation.acknowledge(listOf(sanction.id)) } }
            if (rest.isEmpty()) onBack()
          }
        }
      }
    }
  }
}

/** One sanction: what happened above, the card with its label and « J'ai compris ». */
@Composable
private fun SanctionCard(
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
