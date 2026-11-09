package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityAuthor
import io.appwin.community.domain.CommunityMemberRole

// Building blocks of the Figma « Appwin InApp » sheets (report, moderation,
// sanctions): the sheet frame, its header action, the info box, form fields
// and check lists. Same pieces as the Swift `CommunitySheetComponents`.

/**
 * Figma sheet: « Annuler » / title / action pill over bg/page, the content
 * scrolling under it. Full height, like the iOS page sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunitySheet(
  title: String,
  leadingLabel: String,
  onDismiss: () -> Unit,
  trailing: @Composable () -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = CommunityColors.background,
    dragHandle = null,
  ) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
      SheetHeader(title, leadingLabel, onDismiss, trailing)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 32.dp)
          .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
      )
    }
  }
}

/** Figma header/default: same row as [TextNavBar], without the status bar inset of a screen. */
@Composable
private fun SheetHeader(
  title: String,
  leadingLabel: String,
  onLeading: () -> Unit,
  trailing: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.weight(1f)) {
      Text(
        text = leadingLabel,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = CommunityColors.textTertiary,
        modifier = Modifier.clickable(onClick = onLeading),
      )
    }
    Text(
      text = title,
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 200.dp),
    )
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing() }
  }
}

internal enum class PillTone { BRAND, CAUTION, ALERT, INVERT }

/**
 * Figma header action (« Valider », « Supprimer », « Épingler »…): 12sp
 * semibold on a rounded 12 fill. `BRAND` follows the studio's accent; the
 * others are the fixed moderation colours.
 */
@Composable
internal fun CommunityActionPill(
  title: String,
  onClick: () -> Unit,
  tone: PillTone = PillTone.BRAND,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  loading: Boolean = false,
) {
  val config = LocalCommunityConfig.current
  val foreground = if (tone == PillTone.BRAND) MaterialTheme.colorScheme.onPrimary else Color.White
  val fill: Brush = when (tone) {
    PillTone.BRAND -> accentPillBrush(config)
    PillTone.CAUTION -> SolidColor(CommunityColors.caution)
    PillTone.ALERT -> SolidColor(CommunityColors.alert)
    PillTone.INVERT -> SolidColor(CommunityColors.invert)
  }
  Row(
    modifier = Modifier
      .alpha(if (enabled && !loading) 1f else 0.5f)
      .clip(RoundedCornerShape(12.dp))
      .background(fill)
      .clickable(enabled = enabled && !loading, onClick = onClick)
      .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (loading) {
      CircularProgressIndicator(color = foreground, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
    } else if (icon != null) {
      Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(12.dp))
    }
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = foreground, maxLines = 1)
  }
}

/** `**bold**` spans, as the Figma info boxes write « @name » or « modérateur ». */
internal fun boldMarkdown(text: String): AnnotatedString = buildAnnotatedString {
  text.split("**").forEachIndexed { index, part ->
    if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
  }
}

/** Figma « info »: bg/low, an info circle, 12sp secondary text. */
@Composable
internal fun CommunityInfoBox(markdown: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(CommunityColors.surfaceMuted)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      SolarModerationIcons.InfoCircle,
      contentDescription = null,
      tint = CommunityColors.textSecondary,
      modifier = Modifier.size(16.dp),
    )
    Text(boldMarkdown(markdown), fontSize = 12.sp, lineHeight = 16.sp, color = CommunityColors.textSecondary)
  }
}

/** Figma « input/text-area »: a label and its counter over a 120dp white field. */
@Composable
internal fun CommunityTextArea(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  strings: CommunityStrings,
  limit: Int = 1000,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = CommunityColors.textPrimary,
        modifier = Modifier.weight(1f),
      )
      Text(
        strings.characterCounter(value.length, limit),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = CommunityColors.textTertiary,
      )
    }
    BasicTextField(
      value = value,
      onValueChange = { onValueChange(it.take(limit)) },
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = CommunityColors.textPrimary),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 120.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(CommunityColors.surface)
        .border(1.dp, CommunityColors.surfaceMuted, RoundedCornerShape(16.dp))
        .padding(20.dp),
      decorationBox = { inner ->
        Box {
          if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = CommunityColors.textTertiary)
          inner()
        }
      },
    )
  }
}

/** Figma « select »: a 40dp white field with the chosen option and a chevron. */
@Composable
internal fun <T> CommunitySelectField(
  label: String,
  options: List<T>,
  selected: T,
  title: (T) -> String,
  onSelect: (T) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textPrimary)
    Box {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(40.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(CommunityColors.surface)
          .border(1.dp, CommunityColors.surfaceMuted, RoundedCornerShape(12.dp))
          .clickable { open = true }
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          title(selected),
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = CommunityColors.textPrimary,
          modifier = Modifier.weight(1f),
        )
        Icon(
          SolarModerationIcons.AltArrowDown,
          contentDescription = null,
          tint = CommunityColors.textTertiary,
          modifier = Modifier.size(12.dp),
        )
      }
      DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        options.forEach { option ->
          DropdownMenuItem(text = { Text(title(option)) }, onClick = { onSelect(option); open = false })
        }
      }
    }
  }
}

/** Figma single-choice list (report and motif sheets): a white card, ruled rows, a check on the chosen one. */
@Composable
internal fun <T> CommunityCheckList(
  options: List<T>,
  selected: T?,
  title: (T) -> String,
  onSelect: (T) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(CommunityColors.surface)
      .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    options.forEachIndexed { index, option ->
      if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(CommunityColors.surfaceMuted))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onSelect(option) }
          .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          title(option),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = CommunityColors.textSecondary,
          modifier = Modifier.weight(1f),
        )
        if (option == selected) {
          Icon(
            SolarModerationIcons.Check,
            contentDescription = null,
            tint = CommunityColors.textPrimary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

internal enum class SanctionKind { HIDDEN, REMOVED }

/** Figma « sanction » on a moderated post: « Supprimé » in red, « Masqué » in amber. */
@Composable
internal fun SanctionBadge(kind: SanctionKind, strings: CommunityStrings) {
  val removed = kind == SanctionKind.REMOVED
  val tint = if (removed) CommunityColors.alert else CommunityColors.caution
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (removed) CommunityColors.alertSoft else CommunityColors.cautionSoft)
      .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Icon(
      if (removed) SolarModerationIcons.Forbidden else SolarModerationIcons.Ghost,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(10.dp),
    )
    Text(
      if (removed) strings.statusRemoved else strings.statusHidden,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = tint,
    )
  }
}

/** Figma « badge/number-s »: the red counter on the flag and the bell. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .heightIn(min = 16.dp)
      .widthIn(min = 16.dp)
      .clip(CircleShape)
      .background(CommunityColors.alert)
      .padding(horizontal = 4.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      if (count > 99) "99+" else "$count",
      fontSize = 10.sp,
      lineHeight = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = Color.White,
    )
  }
}

/** An author's avatar with the Figma admin badge (5:1837) for the community's team. */
@Composable
internal fun AuthorAvatar(author: CommunityAuthor?, size: Int, modifier: Modifier = Modifier) {
  Box(modifier = modifier) {
    CommunityAvatar(author?.avatarUrl, author?.nickname.orEmpty().ifBlank { "?" }, size = size)
    if (author != null && (author.role == CommunityMemberRole.ADMIN || author.isTeam)) {
      AdminBadge(
        avatarSize = size,
        modifier = Modifier.align(Alignment.BottomEnd).offset(x = (size * 0.08f).dp, y = (size * 0.08f).dp),
      )
    }
  }
}

/** Figma « badge admin »: an indigo disc with a white shield star; 16dp on a 40dp avatar. */
@Composable
internal fun AdminBadge(avatarSize: Int, modifier: Modifier = Modifier) {
  val size = maxOf(10f, avatarSize * 0.4f)
  Box(
    modifier = modifier.size(size.dp).clip(CircleShape).background(CommunityColors.adminBadge),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      SolarModerationIcons.ShieldStar,
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size((size * 0.625f).dp),
    )
  }
}

/** The member a sanction sheet is about: their avatar and name, centred. */
@Composable
internal fun MemberHeader(nickname: String, avatarUrl: String?) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    CommunityAvatar(avatarUrl, nickname, size = 40)
    Text(nickname, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CommunityColors.textPrimary)
  }
}

/** A failed sheet action, under its form: the sheet stays open to retry. */
@Composable
internal fun CommunityErrorLine(strings: CommunityStrings) {
  Text(strings.actionFailed, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CommunityColors.alert)
}
