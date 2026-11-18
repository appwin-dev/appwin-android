package io.appwin.community.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.MemberContact
import kotlinx.coroutines.delay

/**
 * A member's email and app user id under their name, for moderators and
 * admins (testimonials, support follow-up). The API sends them to no one else.
 */
@Composable
internal fun ProfileContactRows(contact: MemberContact, strings: CommunityStrings, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    contact.email?.let { ContactRow(strings.profileEmail, it, strings) }
    contact.externalId?.let { ContactRow(strings.profileAppUserId, it, strings) }
  }
}

@Composable
private fun ContactRow(label: String, value: String, strings: CommunityStrings) {
  val clipboard = LocalClipboardManager.current
  var copied by remember(value) { mutableStateOf(false) }
  LaunchedEffect(copied) {
    if (copied) {
      delay(1_500)
      copied = false
    }
  }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, fontSize = 12.sp, color = CommunityColors.textTertiary)
    // The copy button stays on screen: the value shrinks first.
    Text(
      value,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    Box(
      modifier = Modifier
        .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
        .clickable(onClickLabel = strings.copyValue(label)) {
          clipboard.setText(AnnotatedString(value))
          copied = true
        }
        .padding(4.dp),
      contentAlignment = Alignment.Center,
    ) {
      if (copied) {
        Text(strings.copied, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
      } else {
        Icon(
          Icons.Outlined.ContentCopy,
          contentDescription = strings.copyValue(label),
          tint = CommunityColors.textTertiary,
          modifier = Modifier.size(12.dp),
        )
      }
    }
  }
}
