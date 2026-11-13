package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityReportReason
import io.appwin.community.domain.ModerationReport
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Figma « Signaler » (155:1259): one reason from the list, sent at once. */
@Composable
internal fun ReportSheet(
  strings: CommunityStrings,
  onDismiss: () -> Unit,
  onSend: suspend (CommunityReportReason) -> Unit,
) {
  var reason by remember { mutableStateOf<CommunityReportReason?>(null) }
  var sending by remember { mutableStateOf(false) }
  var sent by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  CommunitySheet(
    title = strings.reportSheetTitle,
    leadingLabel = strings.cancel,
    onDismiss = onDismiss,
    trailing = {
      if (!sent) {
        CommunityActionPill(
          title = strings.validate,
          tone = PillTone.ALERT,
          enabled = reason != null,
          loading = sending,
          onClick = {
            val picked = reason ?: return@CommunityActionPill
            sending = true
            failed = false
            scope.launch {
              if (runCatching { onSend(picked) }.isSuccess) {
                sent = true
                delay(1_200)
                onDismiss()
              } else {
                failed = true
              }
              sending = false
            }
          },
        )
      }
    },
  ) {
    Text(
      if (sent) strings.reportSent else strings.reportQuestion,
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textPrimary,
    )
    if (!sent) {
      CommunityCheckList(CommunityReportReason.entries, reason, strings::reportReason) { reason = it }
      if (failed) CommunityErrorLine(strings)
    }
  }
}

/** Figma « N signalements » (178:3145): why members reported a content. */
@Composable
internal fun ReportsListSheet(
  reports: List<ModerationReport>,
  wasAutoHidden: Boolean,
  strings: CommunityStrings,
  onDismiss: () -> Unit,
) {
  val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
  CommunitySheet(
    title = strings.reportsCount(reports.size),
    leadingLabel = strings.back,
    onDismiss = onDismiss,
  ) {
    if (wasAutoHidden) CommunityInfoBox(strings.reportsAutoHidden(reports.size))
    Column {
      reports.forEachIndexed { index, report ->
        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(CommunityColors.surfaceMuted))
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            SolarModerationIcons.Flag,
            contentDescription = null,
            tint = CommunityColors.caution,
            modifier = Modifier.size(16.dp),
          )
          Text(
            strings.reasonLabel(report.reason),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = CommunityColors.caution,
            modifier = Modifier.weight(1f),
          )
          Text(format.format(Date(report.createdAtMillis)), fontSize = 12.sp, color = CommunityColors.textTertiary)
        }
      }
    }
  }
}
