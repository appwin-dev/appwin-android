package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityReactionCount
import io.appwin.community.domain.CommunityReactionKind
import kotlinx.coroutines.withTimeoutOrNull

/** Figma emoji bar (178:2561): opened by a long press, scrolls when the studio offers more than fit. */
@Composable
internal fun ReactionBar(
  reactions: List<CommunityReactionKind>,
  selected: CommunityReactionKind?,
  strings: CommunityStrings,
  onPick: (CommunityReactionKind) -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(999.dp)
  Row(
    modifier = modifier
      .widthIn(max = 300.dp)
      .shadow(12.dp, shape, clip = false, ambientColor = BarShadow, spotColor = BarShadow)
      .clip(shape)
      .background(CommunityColors.surface)
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    reactions.forEach { kind ->
      Box(
        modifier = Modifier
          .size(28.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (kind == selected) CommunityColors.surfaceMuted else Color.Transparent)
          .clickable(onClickLabel = strings.reactionLabel(kind)) { onPick(kind) },
        contentAlignment = Alignment.Center,
      ) {
        Text(kind.emoji, fontSize = 20.sp)
      }
    }
  }
}

private val BarShadow = Color.Black.copy(alpha = 0.12f)

/** Figma reaction summary: the three most used emojis, tight, then the count. */
@Composable
internal fun ReactionSummary(top: List<CommunityReactionKind>, count: Int? = null) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
      top.take(3).forEach { Text(it.emoji, fontSize = 12.sp) }
    }
    if (count != null) {
      Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textTertiary)
    }
  }
}

/**
 * Tap, and a long press that fires as soon as it is held (with a haptic), not
 * on release. Releasing a long press never also counts as a tap, and a finger
 * that moves past the touch slop is a scroll: neither fires. Children that
 * handle the touch themselves (buttons in a comment) keep it.
 */
internal fun Modifier.communityPress(onTap: () -> Unit, onLongPress: () -> Unit): Modifier = composed {
  val haptics = LocalHapticFeedback.current
  val tap by rememberUpdatedState(onTap)
  val longPress by rememberUpdatedState(onLongPress)
  pointerInput(Unit) {
    awaitEachGesture {
      val down = awaitFirstDown()
      var moved = false
      val up = withTimeoutOrNull(LONG_PRESS_MILLIS) {
        while (true) {
          val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
          if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
            moved = true
            break
          }
          if (change.changedToUp()) return@withTimeoutOrNull change
        }
        null
      }
      when {
        moved -> Unit
        up != null -> {
          up.consume()
          tap()
        }
        else -> {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          longPress()
          // The rest of this touch belongs to the long press.
          do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
          } while (event.changes.any { it.pressed })
        }
      }
    }
  }
}

private const val LONG_PRESS_MILLIS = 250L

/**
 * Which post or comment has its emoji bar open on the screen (Figma 178:2456):
 * that one comes forward, everything else dims and only closes the bar on tap.
 *
 * Each element dims itself rather than one backdrop covering the screen: a
 * lazy list draws its rows in order, so later rows would cover a shared one.
 */
@Stable
internal class ReactionFocus {
  var focusedId: String? by mutableStateOf(null)
}

private val LocalReactionFocus = staticCompositionLocalOf<ReactionFocus?> { null }

/** One per screen holding posts or comments. */
@Composable
internal fun ReactionFocusHost(content: @Composable () -> Unit) {
  val focus = remember { ReactionFocus() }
  CompositionLocalProvider(LocalReactionFocus provides focus, content = content)
}

/**
 * A post or comment that can open its emoji bar: forward while [active],
 * dimmed while another one is. [onActiveChange] closes it when the focus moves.
 */
internal fun Modifier.reactionFocusable(
  id: String,
  active: Boolean,
  onActiveChange: (Boolean) -> Unit,
  cornerRadius: Dp,
): Modifier = composed {
  val focus = LocalReactionFocus.current
  val onChange by rememberUpdatedState(onActiveChange)
  if (focus != null) {
    LaunchedEffect(active) {
      if (active) focus.focusedId = id else if (focus.focusedId == id) focus.focusedId = null
    }
    LaunchedEffect(focus.focusedId) {
      if (focus.focusedId != id && active) onChange(false)
    }
  }
  val lifted = Modifier
    .scale(if (active) 1.02f else 1f)
    .shadow(if (active) 16.dp else 0.dp, RoundedCornerShape(cornerRadius), clip = false)
  val dimmed = focus?.focusedId != null && focus.focusedId != id
  lifted.then(if (dimmed && focus != null) dimLayer(focus, cornerRadius) else Modifier)
}

/**
 * Anything else on the screen (header, buttons, page): dimmed while a bar is
 * open. [ownerId] is the post or comment it belongs to (a pinned badge): it
 * stays bright with its owner.
 */
internal fun Modifier.reactionDimmed(cornerRadius: Dp = 0.dp, ownerId: String? = null): Modifier = composed {
  val focus = LocalReactionFocus.current
  val focused = focus?.focusedId
  if (focus != null && focused != null && focused != ownerId) dimLayer(focus, cornerRadius) else Modifier
}

/** The grey veil; it also swallows the touch, which only closes the bar. */
private fun dimLayer(focus: ReactionFocus, cornerRadius: Dp): Modifier = Modifier
  .drawWithContent {
    drawContent()
    drawRoundRect(DimColor, cornerRadius = CornerRadius(cornerRadius.toPx()))
  }
  .pointerInput(focus) {
    awaitEachGesture {
      awaitFirstDown(pass = PointerEventPass.Initial).consume()
      waitForUpOrCancellation(PointerEventPass.Initial)?.consume()
      focus.focusedId = null
    }
  }

private val DimColor = Color.Black.copy(alpha = 0.18f)

/** The page behind the cards: dimmed while a bar is open, drawn under the content. */
internal fun Modifier.reactionDimBehind(): Modifier = composed {
  val focus = LocalReactionFocus.current
  if (focus?.focusedId != null) drawBehind { drawRect(DimColor) } else Modifier
}

/** The like glyph: the outline heart, filled red once the reader has reacted. */
@Composable
internal fun HeartGlyph(filled: Boolean, size: Dp = 20.dp) {
  Icon(
    imageVector = if (filled) SolarIcons.HeartBold else SolarIcons.Heart,
    contentDescription = null,
    tint = if (filled) CommunityColors.like else CommunityColors.textTertiary,
    modifier = Modifier.size(size),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReactionBreakdownSheet(
  counts: List<CommunityReactionCount>,
  total: Int,
  strings: CommunityStrings,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Text(
      text = strings.reactionsTitle(total),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    counts.forEach { entry ->
      ListItem(
        headlineContent = { Text(strings.reactionLabel(entry.kind)) },
        leadingContent = {
          Text(text = entry.kind.emoji, fontSize = 22.sp)
        },
        trailingContent = {
          Text(
            text = entry.count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = CommunityColors.textSecondary,
          )
        },
      )
    }
    TextButton(
      onClick = onDismiss,
      modifier = Modifier.fillMaxWidth().padding(24.dp),
    ) { Text(strings.close) }
  }
}
