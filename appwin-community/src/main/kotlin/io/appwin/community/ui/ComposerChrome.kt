package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom action bar - same chrome as iOS `composerActionBar` / Support InputMessage:
 * photo · video · poll on the left, optional counter, Send on the right.
 */
@Composable
internal fun ComposerActionBar(
  strings: CommunityStrings,
  imagesEnabled: Boolean,
  videosEnabled: Boolean,
  canAddPhoto: Boolean,
  showsPoll: Boolean,
  allowPoll: Boolean = true,
  showsCounter: Boolean,
  remaining: Int,
  canSubmit: Boolean,
  sendLabel: String = strings.publish,
  sendBrush: Brush,
  sendShadow: Color?,
  onAddPhoto: () -> Unit,
  onAddVideo: () -> Unit,
  onTogglePoll: () -> Unit,
  onSend: () -> Unit,
) {
  val radius = LocalCommunityConfig.current.theme.radius.dp
  // Figma create-post (19:4974): no divider, 20 gutters, chips then the pill.
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(CommunityColors.background)
      // Union, not chained padding: ime already covers the nav-bar band when
      // open; summing both leaves a blank gap above the keyboard.
      .windowInsetsPadding(communityBottomWithImeInsets())
      .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (imagesEnabled) {
      ComposerActionIcon(
        icon = SolarIcons.Gallery,
        label = strings.addPhoto,
        enabled = canAddPhoto,
        onClick = onAddPhoto,
      )
    }
    if (videosEnabled) {
      ComposerActionIcon(
        icon = SolarIcons.Videocamera,
        label = strings.addVideo,
        enabled = canAddPhoto,
        onClick = onAddVideo,
      )
    }
    if (allowPoll) {
      ComposerActionIcon(
        icon = SolarIcons.Chart,
        activeIcon = SolarIcons.ChartBold,
        label = strings.addPoll,
        active = showsPoll,
        onClick = onTogglePoll,
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    if (showsCounter) {
      Text(
        text = "$remaining",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (remaining < 0) {
          MaterialTheme.colorScheme.error
        } else {
          CommunityColors.textTertiary
        },
      )
    }

    val pillShape = RoundedCornerShape(minOf(radius, 16).dp)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .shadow(
          elevation = if (canSubmit && sendShadow != null) 4.dp else 0.dp,
          shape = pillShape,
          ambientColor = if (canSubmit) sendShadow ?: Color.Transparent else Color.Transparent,
          spotColor = if (canSubmit) sendShadow ?: Color.Transparent else Color.Transparent,
        )
        .clip(pillShape)
        .background(sendBrush)
        .alpha(if (canSubmit) 1f else 0.5f)
        .clickable(enabled = canSubmit, onClick = onSend)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Icon(
        imageVector = SolarIcons.Plain,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = sendLabel,
        fontSize = 16.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
  }
}

@Composable
private fun ComposerActionIcon(
  icon: ImageVector,
  label: String,
  enabled: Boolean = true,
  active: Boolean = false,
  activeIcon: ImageVector = icon,
  onClick: () -> Unit,
) {
  // Figma chip (19:5129): bg/low square, half the card radius; the open tool is
  // outlined and switches to the Bold glyph.
  val shape = RoundedCornerShape(LocalCommunityConfig.current.radiusField)
  Box(
    modifier = Modifier
      .size(32.dp)
      .clip(shape)
      .background(CommunityColors.surfaceMuted)
      .then(if (active) Modifier.border(1.dp, CommunityColors.textPrimary, shape) else Modifier)
      .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = if (active) activeIcon else icon,
      contentDescription = label,
      tint = when {
        active -> CommunityColors.textPrimary
        enabled -> CommunityColors.textTertiary
        else -> CommunityColors.textTertiary.copy(alpha = 0.5f)
      },
      modifier = Modifier.size(16.dp),
    )
  }
}

/** Figma 19:4560: who the post will show, a shortcut to the profile, a close. */
@Composable
internal fun AnonymousBanner(
  nickname: String,
  strings: CommunityStrings,
  onEditProfile: () -> Unit,
  onDismiss: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(CommunityColors.surfaceMuted)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(strings.anonymousBannerTitle, fontSize = 12.sp, color = CommunityColors.textSecondary)
      Text("@$nickname", fontSize = 12.sp, color = CommunityColors.textSecondary)
    }
    Text(
      strings.editProfileShort,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = CommunityColors.textPrimary,
      modifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(CommunityColors.surface)
        .clickable(onClick = onEditProfile)
        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 7.dp),
    )
    Box(
      modifier = Modifier.size(24.dp).clickable(onClickLabel = strings.dismiss, onClick = onDismiss),
      contentAlignment = Alignment.Center,
    ) {
      Icon(SolarModerationIcons.Close, contentDescription = null, tint = CommunityColors.textPrimary, modifier = Modifier.size(16.dp))
    }
  }
}
