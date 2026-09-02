package io.appwin.support.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * The messenger as a sheet over the host app - Figma 441:10486 / 470:6959.
 *
 * One panel for the whole product: opening a conversation does not push a new
 * screen, it swaps the content inside this sheet. [onDismissed] is called once
 * the hide animation has run, so the activity finishes on an empty screen
 * rather than cutting the motion short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessengerSheet(onDismissed: () -> Unit) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()

  ModalBottomSheet(
    onDismissRequest = onDismissed,
    sheetState = sheetState,
    shape = RoundedCornerShape(
      topStart = SupportTokens.sheetRadius,
      topEnd = SupportTokens.sheetRadius,
    ),
    containerColor = SupportTokens.sheetBackground,
    contentColor = SupportTokens.textMain,
    scrimColor = SupportTokens.scrim,
    // The design has no grabber, and the header already carries a close button.
    dragHandle = null,
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
  ) {
    MessengerRoot(
      // The sheet is sized by its content, and the home screen is short: without
      // a floor it would open as a stub barely taller than the call to action.
      modifier = Modifier.fillMaxHeight(0.92f),
      onClose = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
          if (!sheetState.isVisible) onDismissed()
        }
      },
    )
  }
}

/**
 * Sheet header - project avatar, title, close.
 *
 * [onBack] adds a back affordance on the screens reached from home. The Figma
 * header does not show one (it was drawn for the root screen), but Android needs
 * a visible way back that matches the system gesture.
 */
@Composable
internal fun SheetHeader(
  title: String,
  logoUrl: String?,
  accent: Color,
  closeLabel: String,
  onBack: (() -> Unit)? = null,
  onClose: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (onBack != null) {
      HeaderIconButton(Icons.Default.ArrowBack, contentDescription = null, onClick = onBack)
    }

    ProjectAvatar(logoUrl, accent)

    Text(
      text = title,
      fontSize = SupportTokens.titleText,
      fontWeight = FontWeight.Medium,
      color = SupportTokens.textMain,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )

    if (onClose != null) {
      HeaderIconButton(Icons.Default.Close, contentDescription = closeLabel, onClick = onClose)
    }
  }
}

/**
 * Header icon with a real touch target.
 *
 * The glyph stays at the 20dp of the design; the 32dp box around it is what the
 * finger actually hits, which the design does not draw.
 */
@Composable
private fun HeaderIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .size(32.dp)
      .clip(CircleShape)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = SupportTokens.textMain,
      modifier = Modifier.size(SupportTokens.closeSize),
    )
  }
}

/** Round project logo, falling back to a flat accent disc. */
@Composable
internal fun ProjectAvatar(
  logoUrl: String?,
  accent: Color,
  size: androidx.compose.ui.unit.Dp = SupportTokens.avatarSize,
) {
  val shape = CircleShape
  if (logoUrl.isNullOrBlank()) {
    Box(Modifier.size(size).clip(shape).background(accent))
  } else {
    AsyncImage(
      model = logoUrl,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(size).clip(shape).background(accent),
    )
  }
}
