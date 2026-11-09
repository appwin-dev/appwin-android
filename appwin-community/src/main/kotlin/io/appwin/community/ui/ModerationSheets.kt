package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityAuthor
import io.appwin.community.domain.CommunityGroup
import io.appwin.community.domain.ModerationReason
import io.appwin.community.domain.PinSettings
import io.appwin.community.domain.SanctionDuration
import kotlinx.coroutines.launch

/**
 * Runs a sheet's action: the pill shows progress, a failure keeps the sheet
 * open with a line under the form, a success closes it.
 */
@Composable
private fun rememberSheetAction(onDone: () -> Unit): SheetAction {
  val scope = rememberCoroutineScope()
  return remember { SheetAction(onDone) }.also { it.scope = scope }
}

private class SheetAction(private val onDone: () -> Unit) {
  var sending by mutableStateOf(false)
  var failed by mutableStateOf(false)
  lateinit var scope: kotlinx.coroutines.CoroutineScope

  fun run(block: suspend () -> Unit) {
    sending = true
    failed = false
    scope.launch {
      val ok = runCatching { block() }.isSuccess
      sending = false
      if (ok) onDone() else failed = true
    }
  }
}

internal enum class ReasonSheetKind { HIDE, REMOVE }

/**
 * Figma « Choisir un motif » (178:3480 delete, 178:3559 hide): the motif goes
 * with the decision, and the author is told it.
 */
@Composable
internal fun ModerationReasonSheet(
  kind: ReasonSheetKind,
  authorName: String,
  strings: CommunityStrings,
  onDismiss: () -> Unit,
  onConfirm: suspend (ModerationReason) -> Unit,
) {
  var reason by remember { mutableStateOf<ModerationReason?>(null) }
  val action = rememberSheetAction(onDismiss)
  val removes = kind == ReasonSheetKind.REMOVE
  CommunitySheet(
    title = strings.chooseReason,
    leadingLabel = strings.cancel,
    onDismiss = onDismiss,
    trailing = {
      CommunityActionPill(
        title = if (removes) strings.delete else strings.hide,
        tone = if (removes) PillTone.ALERT else PillTone.CAUTION,
        enabled = reason != null,
        loading = action.sending,
        onClick = { reason?.let { picked -> action.run { onConfirm(picked) } } },
      )
    },
  ) {
    CommunityInfoBox(if (removes) strings.removeInfo(authorName) else strings.hideInfo(authorName))
    CommunityCheckList(ModerationReason.entries, reason, strings::moderationReason) { reason = it }
    if (action.failed) CommunityErrorLine(strings)
  }
}

/** Figma « Choisir un groupe » (178:3909): moves a post to another group. */
@Composable
internal fun GroupPickerSheet(
  groups: List<CommunityGroup>,
  currentGroupId: String,
  strings: CommunityStrings,
  onDismiss: () -> Unit,
  onMove: suspend (String) -> Unit,
) {
  var selection by remember { mutableStateOf(currentGroupId) }
  val action = rememberSheetAction(onDismiss)
  CommunitySheet(
    title = strings.chooseGroupTitle,
    leadingLabel = strings.cancel,
    onDismiss = onDismiss,
    trailing = {
      CommunityActionPill(
        title = strings.moveAction,
        enabled = selection != currentGroupId,
        loading = action.sending,
        onClick = { action.run { onMove(selection) } },
      )
    },
  ) {
    CommunityCheckList(
      options = groups.map { it.id },
      selected = selection,
      title = { id -> groups.firstOrNull { it.id == id }?.let { listOfNotNull(it.emoji, it.name).joinToString(" ") }.orEmpty() },
      onSelect = { selection = it },
    )
    if (action.failed) CommunityErrorLine(strings)
  }
}

/**
 * Figma « Régler l'épinglage » (178:3968): an end date, a view cap per member,
 * or both; the pin lifts at the first one reached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PinSettingsSheet(
  strings: CommunityStrings,
  onDismiss: () -> Unit,
  onPin: suspend (PinSettings) -> Unit,
) {
  val now = remember { System.currentTimeMillis() }
  var usesDate by remember { mutableStateOf(true) }
  var usesViews by remember { mutableStateOf(false) }
  var views by remember { mutableStateOf("2") }
  val dateState = rememberDatePickerState(
    initialSelectedDateMillis = now + 7 * DAY_MILLIS,
    selectableDates = object : SelectableDates {
      override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= now - DAY_MILLIS
    },
  )
  val action = rememberSheetAction(onDismiss)
  CommunitySheet(
    title = strings.pinTitle,
    leadingLabel = strings.cancel,
    onDismiss = onDismiss,
    trailing = {
      CommunityActionPill(
        title = strings.pinAction,
        enabled = usesDate || usesViews,
        loading = action.sending,
        onClick = {
          // End of the chosen day: « until » that date includes it.
          val until = dateState.selectedDateMillis?.takeIf { usesDate }?.plus(DAY_MILLIS - 1)
          val cap = views.toIntOrNull()?.coerceAtLeast(1)?.takeIf { usesViews }
          action.run { onPin(PinSettings(until, cap)) }
        },
      )
    },
  ) {
    CommunityInfoBox(strings.pinInfo)
    PinCriterion(strings.pinUntilTitle, strings.pinUntilHint, usesDate, { usesDate = it }) {
      DatePicker(
        state = dateState,
        title = null,
        headline = null,
        showModeToggle = false,
        colors = DatePickerDefaults.colors(containerColor = CommunityColors.surface),
      )
    }
    PinCriterion(strings.pinViewsTitle, strings.pinViewsHint, usesViews, { usesViews = it }) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
          value = views,
          onValueChange = { typed -> views = typed.filter(Char::isDigit).take(4) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = CommunityColors.textPrimary),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          modifier = Modifier
            .width(74.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CommunityColors.surface)
            .border(1.dp, CommunityColors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Text(strings.pinTimes, fontSize = 14.sp, color = CommunityColors.textPrimary)
      }
    }
    if (action.failed) CommunityErrorLine(strings)
  }
}

private const val DAY_MILLIS = 86_400_000L

@Composable
private fun PinCriterion(
  title: String,
  hint: String,
  on: Boolean,
  onToggle: (Boolean) -> Unit,
  body: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(CommunityColors.surface)
      .border(1.dp, CommunityColors.surfaceMuted, RoundedCornerShape(16.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textPrimary)
        Text(hint, fontSize = 12.sp, color = CommunityColors.textTertiary)
      }
      Switch(
        checked = on,
        onCheckedChange = onToggle,
        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
      )
    }
    if (on) body()
  }
}

internal enum class MemberSanctionKind { WARN, SHADOW_BAN, BAN }

/**
 * Figma « Avertissement » (178:3803), « Shadow-Ban » (200:4345) and « Bannir »
 * (200:4370): a sanction on a member, with its message or motif.
 */
@Composable
internal fun MemberSanctionSheet(
  kind: MemberSanctionKind,
  member: CommunityAuthor,
  strings: CommunityStrings,
  onDismiss: () -> Unit,
  onSend: suspend (text: String?, durationHours: Int?) -> Unit,
) {
  var duration by remember { mutableStateOf(SanctionDuration.PERMANENT) }
  var text by remember { mutableStateOf("") }
  val trimmed = text.trim()
  val action = rememberSheetAction(onDismiss)
  val (title, info) = when (kind) {
    MemberSanctionKind.WARN -> strings.warnTitle to strings.warnInfo(member.nickname)
    MemberSanctionKind.SHADOW_BAN -> strings.shadowBanTitle to strings.shadowBanInfo(member.nickname)
    MemberSanctionKind.BAN -> strings.banTitle to strings.banInfo(member.nickname)
  }
  CommunitySheet(
    title = title,
    leadingLabel = strings.cancel,
    onDismiss = onDismiss,
    trailing = {
      CommunityActionPill(
        title = if (kind == MemberSanctionKind.WARN) strings.send else strings.validate,
        tone = if (kind == MemberSanctionKind.BAN) PillTone.ALERT else PillTone.CAUTION,
        // A warning without a message says nothing.
        enabled = kind != MemberSanctionKind.WARN || trimmed.isNotEmpty(),
        loading = action.sending,
        onClick = {
          val hours = if (kind == MemberSanctionKind.WARN) null else duration.hours
          action.run { onSend(trimmed.ifEmpty { null }, hours) }
        },
      )
    },
  ) {
    CommunityInfoBox(info)
    MemberHeader(member.nickname, member.avatarUrl)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
      if (kind != MemberSanctionKind.WARN) {
        CommunitySelectField(
          label = strings.chooseDuration,
          options = SanctionDuration.entries,
          selected = duration,
          title = strings::duration,
          onSelect = { duration = it },
        )
      }
      CommunityTextArea(
        label = if (kind == MemberSanctionKind.WARN) strings.message else strings.motive,
        value = text,
        onValueChange = { text = it },
        placeholder = if (kind == MemberSanctionKind.SHADOW_BAN) strings.teamOnlyPlaceholder else strings.visibleBy(member.nickname),
        strings = strings,
      )
    }
    if (action.failed) CommunityErrorLine(strings)
  }
}
