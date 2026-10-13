package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.appwin.community.domain.CommunityMedia

/**
 * Comment field - Figma `interaction-footer`: the member's avatar, a bg/low
 * pill field and the accent send button, on the container colour.
 */
@Composable
internal fun CommentComposer(
  avatarUrl: String?,
  avatarName: String,
  value: String,
  placeholder: String,
  maxLength: Int,
  sending: Boolean,
  uploading: Boolean,
  canSend: Boolean,
  replyToLabel: String?,
  pendingMedia: List<CommunityMedia>,
  imagesEnabled: Boolean,
  canAddPhoto: Boolean,
  photoLabel: String,
  onChange: (String) -> Unit,
  onClearReply: () -> Unit,
  onRemoveMedia: (Int) -> Unit,
  onAddPhoto: () -> Unit,
  onSend: () -> Unit,
) {
  val config = LocalCommunityConfig.current
  val fieldShape = RoundedCornerShape(config.radiusCard)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      // Figma drop shadow 0 -24 20: elevation only casts downward, so the halo
      // above the bar is a fade drawn outside its bounds.
      .drawBehind {
        val height = 24.dp.toPx()
        drawRect(
          brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x14171717)),
            startY = -height,
            endY = 0f,
          ),
          topLeft = Offset(0f, -height),
          size = Size(size.width, height),
        )
      }
      .background(CommunityColors.surface)
      // Union, not chained padding: ime already covers the nav-bar band when
      // open; summing both leaves a blank gap above the keyboard.
      .windowInsetsPadding(communityBottomWithImeInsets()),
  ) {
    if (replyToLabel != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 20.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = replyToLabel,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = CommunityColors.textTertiary,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClearReply, modifier = Modifier.size(28.dp)) {
          Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = CommunityColors.textTertiary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
    if (pendingMedia.isNotEmpty() || uploading) {
      LazyRow(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(pendingMedia.size) { index ->
          Box {
            AsyncImage(
              model = pendingMedia[index].url,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            )
            Icon(
              Icons.Default.Close,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onRemoveMedia(index) }
                .padding(2.dp),
            )
          }
        }
        if (uploading) {
          item {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CommunityColors.surfaceMuted),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
              )
            }
          }
        }
      }
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      CommunityAvatar(avatarUrl, avatarName.ifBlank { "?" }, size = 32)
      Row(
        modifier = Modifier
          .weight(1f)
          .clip(fieldShape)
          .background(CommunityColors.surfaceMuted)
          .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BasicTextField(
          value = value,
          // The limit applies while typing, not on send: being refused text you
          // have just written is the worst moment to learn it is too long.
          onValueChange = { if (it.length <= maxLength) onChange(it) },
          textStyle = TextStyle(fontSize = 14.sp, color = CommunityColors.textPrimary),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          maxLines = 4,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          modifier = Modifier.weight(1f).padding(vertical = 10.dp),
          decorationBox = { field ->
            Box {
              if (value.isEmpty()) {
                Text(text = placeholder, fontSize = 14.sp, color = CommunityColors.textTertiary)
              }
              field()
            }
          },
        )
        if (imagesEnabled) {
          Icon(
            SolarIcons.Gallery,
            contentDescription = photoLabel,
            tint = CommunityColors.textTertiary.copy(alpha = if (canAddPhoto) 1f else 0.4f),
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .clickable(enabled = canAddPhoto, onClick = onAddPhoto)
              .padding(6.dp),
          )
        }
      }
      Box(
        modifier = Modifier
          .size(38.dp)
          .alpha(if (canSend) 1f else 0.4f)
          .clip(fieldShape)
          .background(accentComposeBrush(config))
          .border(2.dp, Color.White.copy(alpha = 0.1f), fieldShape)
          .clickable(enabled = canSend, onClick = onSend),
        contentAlignment = Alignment.Center,
      ) {
        if (sending) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(
            SolarIcons.ArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}
