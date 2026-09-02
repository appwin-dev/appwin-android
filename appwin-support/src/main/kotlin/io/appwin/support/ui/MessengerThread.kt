package io.appwin.support.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.appwin.support.domain.Message
import io.appwin.support.domain.MessengerConfig

/**
 * Conversation thread - Figma 470:6959.
 *
 * Same sheet and same header as home: the thread is a screen inside the panel,
 * not a page over it.
 */
@Composable
internal fun ThreadScreen(
  viewModel: SupportViewModel,
  strings: SupportStrings,
  onBack: () -> Unit,
  onClose: (() -> Unit)?,
) {
  val thread by viewModel.thread.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val config = state.config
  val listState = rememberLazyListState()
  var draft by remember { mutableStateOf("") }

  // A thread reads from the bottom: follow every message received or sent.
  LaunchedEffect(thread.messages.size) {
    if (thread.messages.isNotEmpty()) listState.animateScrollToItem(thread.messages.lastIndex)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      // The union, not `imePadding()` alone: the sheet zeroes its own insets so
      // the panel can reach the top, which leaves the composer sitting under the
      // navigation bar whenever the keyboard is closed. Taking the larger of the
      // two clears the bar at rest and the keyboard when it opens, without
      // stacking both.
      .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
      .padding(SupportTokens.sheetPadding),
    verticalArrangement = Arrangement.spacedBy(SupportTokens.sectionGap),
  ) {
    SheetHeader(
      title = config.agentTitle(strings),
      logoUrl = config.agentAvatar,
      accent = config.accentColor,
      closeLabel = strings.close,
      onBack = onBack,
      onClose = onClose,
    )

    Box(Modifier.weight(1f)) {
      when {
        thread.isLoading -> Centered { CircularProgressIndicator() }

        else -> LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(SupportTokens.itemGap),
        ) {
          if (config.messaging.welcomeMessageEnabled) {
            config.messaging.welcomeMessage?.let { welcome ->
              item(key = "welcome") { WelcomeBanner(welcome) }
            }
          }

          items(thread.messages, key = { it.id }) { message ->
            MessageBubble(message, config, strings)
          }
        }
      }
    }

    Composer(
      draft = draft,
      onDraftChange = { draft = it },
      sending = thread.sending,
      accent = config.accentColor,
      onAccent = config.onAccentColor,
      radius = config.design.radius.dp.dp,
      strings = strings,
      onSend = {
        viewModel.sendMessage(draft) { draft = "" }
        draft = ""
      },
    )
  }
}

/* -------------------------------------------------------------------------- */
/* Welcome banner (470:6972)                                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun WelcomeBanner(text: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(SupportTokens.welcomeRadius))
      .background(SupportTokens.surfaceMuted)
      .padding(SupportTokens.cardPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      Icons.Default.Info,
      contentDescription = null,
      tint = SupportTokens.textSecondary,
      modifier = Modifier.size(SupportTokens.smallIconSize),
    )
    Text(
      text = text,
      fontSize = SupportTokens.captionText,
      fontWeight = FontWeight.Medium,
      lineHeight = SupportTokens.captionText * 1.4f,
      color = SupportTokens.textSecondary,
      modifier = Modifier.weight(1f),
    )
  }
}

/* -------------------------------------------------------------------------- */
/* Bubbles (470:7445 inbound, 470:7450 outbound)                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun MessageBubble(message: Message, config: MessengerConfig, strings: SupportStrings) {
  val fromStudio = message.authorType.isStudio

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (fromStudio) Alignment.Start else Alignment.End,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      // The arrangement is what pins a short bubble to its side. A spacer with a
      // weight would split the row in two instead, leaving it stranded mid-line.
      horizontalArrangement = Arrangement.spacedBy(
        SupportTokens.bubbleGap,
        if (fromStudio) Alignment.Start else Alignment.End,
      ),
      // The avatar sits level with the bottom of the bubble, so the footer has
      // to stay outside this row: inside it, the row would grow and leave the
      // avatar floating beside the timestamp instead.
      verticalAlignment = Alignment.Bottom,
    ) {
      // `fill = false` caps a long bubble at the width left over by the avatar
      // without stretching a short one to it.
      if (fromStudio) {
        BubbleAvatar(config.agentAvatar, config.accentColor)
        BubbleBody(message, config, fromStudio = true, modifier = Modifier.weight(1f, false))
      } else {
        BubbleBody(message, config, fromStudio = false, modifier = Modifier.weight(1f, false))
        BubbleAvatar(config.context.projectLogoUrl, config.accentColor)
      }
    }

    BubbleFooter(message, strings, fromStudio)
  }
}

@Composable
private fun BubbleAvatar(url: String?, accent: Color) {
  ProjectAvatar(logoUrl = url, accent = accent, size = SupportTokens.avatarSize)
}

/**
 * The bubble itself.
 *
 * The tail is the one corner that stays nearly square, on the side the avatar
 * sits: it is what tells the two speakers apart at a glance, so it is not
 * driven by the studio's radius setting.
 */
@Composable
private fun BubbleBody(
  message: Message,
  config: MessengerConfig,
  fromStudio: Boolean,
  modifier: Modifier = Modifier,
) {
  val r = SupportTokens.bubbleRadius
  val tail = SupportTokens.bubbleTailRadius
  val shape = if (fromStudio) {
    RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = r, bottomStart = 0.dp)
  } else {
    RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = tail, bottomStart = r)
  }

  Column(
      modifier = modifier
        .widthIn(max = 280.dp)
        .then(
          if (fromStudio) Modifier
          else Modifier.shadow(8.dp, shape, ambientColor = Color(0x1A020617), spotColor = Color(0x1A020617)),
        )
        .clip(shape)
        .background(if (fromStudio) SupportTokens.surface else config.accentColor)
        .padding(SupportTokens.cardPadding),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      message.attachments.filter { it.isImage }.forEach { attachment ->
        AsyncImage(
          model = attachment.url,
          contentDescription = attachment.filename,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .size(180.dp)
            .clip(RoundedCornerShape(SupportTokens.bubbleTailRadius * 4)),
        )
      }
      if (message.body.isNotBlank()) {
        Text(
          text = message.body,
          fontSize = SupportTokens.bodyText,
          fontWeight = FontWeight.Medium,
          lineHeight = SupportTokens.bodyText * 1.4f,
          color = if (fromStudio) SupportTokens.textMain else config.onAccentColor,
        )
      }
  }
}

@Composable
private fun BubbleFooter(message: Message, strings: SupportStrings, fromStudio: Boolean) {
  val stamp = buildString {
    append(relativeTime(message.createdAtMillis))
    // The read receipt only shows on our own messages: on the studio's, it
    // tells the user nothing.
    if (!fromStudio && message.readAtMillis != null) append(" - ").append(strings.seen)
  }
  if (stamp.isBlank() && message.reactions.isEmpty()) return

  // Cleared past the avatar on its own side, so the stamp lines up with the
  // bubble's edge rather than the avatar's.
  val indent = SupportTokens.avatarSize + SupportTokens.bubbleGap
  Row(
    modifier = Modifier.padding(
      top = 4.dp,
      start = if (fromStudio) indent else 4.dp,
      end = if (fromStudio) 4.dp else indent,
    ),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    message.reactions.forEach { reaction ->
      Text(
        text = "${reaction.emoji} ${reaction.count}",
        fontSize = SupportTokens.captionText,
        color = SupportTokens.textTertiary,
      )
    }
    Text(
      text = stamp,
      fontSize = SupportTokens.captionText,
      color = SupportTokens.textTertiary,
    )
  }
}

/* -------------------------------------------------------------------------- */
/* Composer                                                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun Composer(
  draft: String,
  onDraftChange: (String) -> Unit,
  sending: Boolean,
  accent: Color,
  onAccent: Color,
  radius: androidx.compose.ui.unit.Dp,
  strings: SupportStrings,
  onSend: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      value = draft,
      onValueChange = onDraftChange,
      placeholder = {
        Text(
          strings.messagePlaceholder,
          fontSize = SupportTokens.bodyText,
          color = SupportTokens.textTertiary,
        )
      },
      textStyle = androidx.compose.ui.text.TextStyle(
        fontSize = SupportTokens.bodyText,
        color = SupportTokens.textMain,
      ),
      shape = RoundedCornerShape(radius),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SupportTokens.surface,
        unfocusedContainerColor = SupportTokens.surface,
        focusedBorderColor = accent,
        unfocusedBorderColor = SupportTokens.borderSubtle,
      ),
      maxLines = 5,
      modifier = Modifier.weight(1f),
    )

    IconButton(
      onClick = onSend,
      enabled = draft.isNotBlank() && !sending,
      modifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(radius))
        .background(if (draft.isNotBlank() && !sending) accent else SupportTokens.border),
    ) {
      if (sending) {
        CircularProgressIndicator(Modifier.size(20.dp), color = onAccent)
      } else {
        Icon(
          Icons.Default.Send,
          contentDescription = strings.send,
          tint = onAccent,
          modifier = Modifier.size(SupportTokens.iconSize),
        )
      }
    }
  }
}

/* -------------------------------------------------------------------------- */
/* Config helpers                                                             */
/* -------------------------------------------------------------------------- */

/** Studio agent name, falling back to the project's own. */
internal fun MessengerConfig.agentTitle(strings: SupportStrings): String =
  messaging.agentName ?: context.agentName.ifBlank { headerTitle(strings) }

/** Studio agent avatar, falling back to the project logo. */
internal val MessengerConfig.agentAvatar: String?
  get() = messaging.agentAvatarUrl ?: context.agentAvatarUrl ?: context.projectLogoUrl
