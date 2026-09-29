package io.appwin.support.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.support.domain.Attachment
import io.appwin.support.domain.Message
import io.appwin.support.domain.MessageReaction
import io.appwin.support.domain.MessengerConfig
import io.appwin.support.domain.QuickMessageReactions

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
  val pending by viewModel.pendingUploads.collectAsStateWithLifecycle()
  val config = state.config
  val customerLanguage = state.customerLanguage
  val listState = rememberLazyListState()
  var draft by remember { mutableStateOf("") }
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val isEditing = thread.editingMessageId != null
  var reactionPickerMessageId by remember { mutableStateOf<String?>(null) }
  // iOS MessageList: tapping the thread resigns first responder and closes the
  // reaction chrome.
  val dismissKeyboard: () -> Unit = {
    reactionPickerMessageId = null
    focusManager.clearFocus(force = true)
    keyboardController?.hide()
  }

  // A thread reads from the bottom: follow every message received or sent,
  // and the typing indicator when it appears.
  LaunchedEffect(thread.messages.size, thread.peerIsTyping) {
    val last = listState.layoutInfo.totalItemsCount - 1
    if (last >= 0) listState.animateScrollToItem(last)
  }

  LaunchedEffect(viewModel) {
    viewModel.attachmentOpens.collect { url ->
      runCatching {
        context.startActivity(
          Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      }
    }
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

    Box(
      modifier = Modifier
        .weight(1f)
        .pointerInput(Unit) {
          detectTapGestures(onTap = { dismissKeyboard() })
        },
    ) {
      when {
        thread.isLoading -> Centered {
          CircularProgressIndicator(color = config.accentColor)
        }

        // No `verticalArrangement`: the gap is not the same everywhere. Inside a
        // burst the bubbles sit close, and only the last of a run is followed by
        // the full spacing, so each item carries its own bottom padding instead.
        else -> {
          val threadItems = remember(thread.messages, customerLanguage, strings.today, strings.yesterday) {
            threadListItems(thread.messages, strings, customerLanguage, context)
          }
          LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
              detectTapGestures(onTap = { dismissKeyboard() })
            },
        ) {
          if (config.messaging.welcomeMessageEnabled) {
            config.messaging.welcomeMessage?.let { welcome ->
              item(key = "welcome") {
                Box(Modifier.padding(bottom = SupportTokens.itemGap)) { WelcomeBanner(welcome) }
              }
            }
          }

          items(threadItems, key = { it.key }) { item ->
            when (item) {
              is ThreadListItem.Day -> DaySeparator(
                label = item.label,
                modifier = Modifier.padding(bottom = SupportTokens.itemGap),
              )
              is ThreadListItem.Bubble -> MessageBubble(
                message = item.grouped.message,
                config = config,
                strings = strings,
                language = customerLanguage,
                isLastInGroup = item.grouped.isLastInGroup,
                pickerOpen = reactionPickerMessageId == item.grouped.message.id,
                onPickerOpenChange = { open ->
                  reactionPickerMessageId = if (open) item.grouped.message.id else null
                },
                onOpenAttachment = viewModel::openAttachment,
                resolveAttachmentUrl = viewModel::resolveAttachmentUrl,
                onEdit = {
                  viewModel.beginEdit(item.grouped.message)?.let { text ->
                    draft = text
                    focusManager.clearFocus(force = false)
                    keyboardController?.show()
                  }
                },
                onDelete = { viewModel.deleteMessage(item.grouped.message.id) },
                onToggleReaction = { emoji ->
                  viewModel.toggleReaction(item.grouped.message.id, emoji)
                },
                modifier = Modifier.padding(
                  bottom = if (item.grouped.isLastInGroup) SupportTokens.itemGap else SupportTokens.runGap,
                ),
              )
            }
          }

          if (thread.peerIsTyping) {
            item(key = "typing") {
              TypingIndicator(
                label = strings.typing,
                modifier = Modifier.padding(bottom = SupportTokens.itemGap),
              )
            }
          }
        }
        }
      }
    }

    Composer(
      draft = draft,
      onDraftChange = {
        draft = it
        if (!isEditing) viewModel.onDraftChange(it)
      },
      pending = pending,
      sending = thread.sending,
      accent = config.accentColor,
      onAccent = config.onAccentColor,
      radius = config.design.radius.dp.dp,
      strings = strings,
      isEditing = isEditing,
      onCancelEdit = {
        viewModel.cancelEdit()
        draft = ""
      },
      onEnqueue = viewModel::enqueueAttachment,
      onRemovePending = viewModel::removePendingUpload,
      onSend = {
        viewModel.sendMessage(draft) { draft = "" }
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

/** A message and whether it closes a run from the same author. */
internal data class GroupedMessage(val message: Message, val isLastInGroup: Boolean)

private sealed interface ThreadListItem {
  val key: String

  data class Day(val id: String, val label: String) : ThreadListItem {
    override val key: String get() = "day-$id"
  }

  data class Bubble(val grouped: GroupedMessage) : ThreadListItem {
    override val key: String get() = grouped.message.id
  }
}

/**
 * Marks consecutive messages from the same author as a run.
 *
 * The bubbles stay separate - that is what tells two sentences apart - but only
 * the last of a run carries the avatar and the timestamp (dashboard / iOS
 * parity). Same author is enough: a one-minute cutoff would split a burst the
 * user typed in one go.
 */
internal fun groupMessages(messages: List<Message>): List<GroupedMessage> =
  messages.mapIndexed { index, message ->
    val next = messages.getOrNull(index + 1)
    val sameRun = next != null && next.authorType == message.authorType
    GroupedMessage(message, isLastInGroup = !sameRun)
  }

private fun threadListItems(
  messages: List<Message>,
  strings: SupportStrings,
  language: String?,
  context: android.content.Context,
): List<ThreadListItem> {
  if (messages.isEmpty()) return emptyList()
  val zone = deviceTimeZone()
  val cal = java.util.Calendar.getInstance(zone)
  val result = mutableListOf<ThreadListItem>()
  var index = 0
  while (index < messages.size) {
    val anchor = messages[index]
    cal.timeInMillis = anchor.createdAtMillis
    val dayStartYear = cal.get(java.util.Calendar.YEAR)
    val dayStartDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val dayMessages = mutableListOf<Message>()
    while (index < messages.size) {
      cal.timeInMillis = messages[index].createdAtMillis
      if (cal.get(java.util.Calendar.YEAR) != dayStartYear ||
        cal.get(java.util.Calendar.DAY_OF_YEAR) != dayStartDay
      ) {
        break
      }
      dayMessages.add(messages[index])
      index += 1
    }
    val dayId = String.format(java.util.Locale.US, "%04d-%03d", dayStartYear, dayStartDay)
    result.add(ThreadListItem.Day(dayId, formatDayLabel(context, strings, language, dayMessages.first().createdAtMillis)))
    groupMessages(dayMessages).forEach { result.add(ThreadListItem.Bubble(it)) }
  }
  return result
}

private fun formatDayLabel(
  context: android.content.Context,
  strings: SupportStrings,
  language: String?,
  millis: Long,
): String {
  val zone = deviceTimeZone()
  val day = java.util.Calendar.getInstance(zone).apply { timeInMillis = millis }
  val now = java.util.Calendar.getInstance(zone)
  if (sameCalendarDay(day, now)) return strings.today
  val yesterday = java.util.Calendar.getInstance(zone).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
  if (sameCalendarDay(day, yesterday)) return strings.yesterday
  val locale = displayLocale(language, context)
  val pattern = "EEEE d MMMM"
  return java.text.SimpleDateFormat(pattern, locale).apply { timeZone = zone }
    .format(java.util.Date(millis))
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

@Composable
private fun DaySeparator(label: String, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .height(1.dp)
        .background(SupportTokens.border),
    )
    Text(
      text = label,
      fontSize = SupportTokens.captionText,
      fontWeight = FontWeight.Medium,
      color = SupportTokens.textTertiary,
    )
    Box(
      modifier = Modifier
        .weight(1f)
        .height(1.dp)
        .background(SupportTokens.border),
    )
  }
}

@Composable
private fun MessageBubble(
  message: Message,
  config: MessengerConfig,
  strings: SupportStrings,
  language: String? = null,
  isLastInGroup: Boolean,
  pickerOpen: Boolean,
  onPickerOpenChange: (Boolean) -> Unit,
  onOpenAttachment: (Attachment) -> Unit,
  resolveAttachmentUrl: suspend (Attachment) -> String,
  onEdit: () -> Unit = {},
  onDelete: () -> Unit = {},
  onToggleReaction: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val fromStudio = message.authorType.isStudio
  val hasAvatar = fromStudio
  val images = message.attachments.filter { it.isImage }
  val videos = message.attachments.filter { it.isVideo }
  val files = message.attachments.filterNot { it.isImage || it.isVideo }
  val mediaOutside = videos + files
  val translated = message.translatedBody?.trim().orEmpty()
  val hasTranslation =
    fromStudio && translated.isNotEmpty() && translated != message.body.trim()
  var showsOriginal by remember(message.id) { mutableStateOf(false) }
  val displayBody =
    if (hasTranslation && !showsOriginal) translated else message.body
  val hasTextOrImage = displayBody.isNotBlank() || images.isNotEmpty()
  val canEditOrDelete =
    !fromStudio && !message.id.startsWith("local:") && message.body.isNotBlank()

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = if (fromStudio) Alignment.Start else Alignment.End,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (pickerOpen) {
      MessageActionChrome(
        message = message,
        strings = strings,
        canEditOrDelete = canEditOrDelete,
        alignEnd = !fromStudio,
        onToggleReaction = { emoji ->
          onPickerOpenChange(false)
          onToggleReaction(emoji)
        },
        onEdit = {
          onPickerOpenChange(false)
          onEdit()
        },
        onDelete = {
          onPickerOpenChange(false)
          onDelete()
        },
      )
    }

    if (hasTextOrImage) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .pointerInput(message.id, pickerOpen) {
            detectTapGestures(
              onTap = { onPickerOpenChange(false) },
              onLongPress = { onPickerOpenChange(true) },
            )
          },
        horizontalArrangement = Arrangement.spacedBy(
          SupportTokens.bubbleGap,
          if (fromStudio) Alignment.Start else Alignment.End,
        ),
        verticalAlignment = Alignment.Bottom,
      ) {
        if (fromStudio) {
          BubbleAvatarSlot(isLastInGroup && mediaOutside.isEmpty(), config.agentAvatar, config.accentColor)
        }
        BubbleWithReactions(
          body = displayBody,
          images = images,
          config = config,
          fromStudio = fromStudio,
          reactions = message.reactions,
          resolveAttachmentUrl = resolveAttachmentUrl,
          onToggleReaction = onToggleReaction,
          modifier = Modifier.weight(1f, false),
        )
      }
    }

    videos.forEach { attachment ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
          SupportTokens.bubbleGap,
          if (fromStudio) Alignment.Start else Alignment.End,
        ),
        verticalAlignment = Alignment.Bottom,
      ) {
        if (fromStudio) {
          val showAvatar = isLastInGroup && files.isEmpty() && attachment.id == videos.last().id
          BubbleAvatarSlot(showAvatar, config.agentAvatar, config.accentColor)
        }
        VideoAttachmentPreview(
          attachment = attachment,
          resolveUrl = resolveAttachmentUrl,
        )
      }
    }

    files.forEach { attachment ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
          SupportTokens.bubbleGap,
          if (fromStudio) Alignment.Start else Alignment.End,
        ),
        verticalAlignment = Alignment.Bottom,
      ) {
        if (fromStudio) {
          val showAvatar = isLastInGroup && attachment.id == files.last().id
          BubbleAvatarSlot(showAvatar, config.agentAvatar, config.accentColor)
        }
        FileAttachmentCard(
          attachment = attachment,
          strings = strings,
          onOpen = onOpenAttachment,
        )
      }
    }

    if (isLastInGroup) {
      BubbleFooter(
        message = message,
        strings = strings,
        language = language,
        fromStudio = fromStudio,
        hasAvatar = hasAvatar,
        hasTranslation = hasTranslation,
        showsOriginal = showsOriginal,
        onToggleOriginal = { showsOriginal = !showsOriginal },
      )
    }
  }
}

@Composable
private fun MessageActionChrome(
  message: Message,
  strings: SupportStrings,
  canEditOrDelete: Boolean,
  alignEnd: Boolean,
  onToggleReaction: (String) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Column(
    horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(
      start = if (alignEnd) 0.dp else SupportTokens.avatarSize + SupportTokens.bubbleGap,
      end = if (alignEnd) 0.dp else 0.dp,
    ),
  ) {
    ReactionPickerPill(
      activeEmojis = message.reactions.filter { it.reactedByMe }.map { it.emoji }.toSet(),
      onSelect = onToggleReaction,
    )
    if (canEditOrDelete) {
      EditDeletePill(
        editLabel = strings.editMessage,
        deleteLabel = strings.deleteMessage,
        onEdit = onEdit,
        onDelete = onDelete,
      )
    }
  }
}

@Composable
private fun ReactionPickerPill(
  activeEmojis: Set<String>,
  onSelect: (String) -> Unit,
) {
  Row(
    modifier = Modifier
      .shadow(12.dp, shape = RoundedCornerShape(999.dp), clip = false)
      .clip(RoundedCornerShape(999.dp))
      .background(SupportTokens.surface)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    QuickMessageReactions.all.forEach { emoji ->
      val active = emoji in activeEmojis
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (active) SupportTokens.surfaceMuted else Color.Transparent)
          .clickable { onSelect(emoji) },
        contentAlignment = Alignment.Center,
      ) {
        Text(text = emoji, fontSize = 22.sp)
      }
    }
  }
}

@Composable
private fun EditDeletePill(
  editLabel: String,
  deleteLabel: String,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    modifier = Modifier
      .shadow(12.dp, shape = RoundedCornerShape(999.dp), clip = false)
      .clip(RoundedCornerShape(999.dp))
      .background(SupportTokens.surface),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier
        .clickable(onClick = onEdit)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Filled.Edit,
        contentDescription = null,
        tint = SupportTokens.textMain,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = editLabel,
        color = SupportTokens.textMain,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
      )
    }
    Box(
      modifier = Modifier
        .width(1.dp)
        .height(20.dp)
        .background(SupportTokens.surfaceMuted),
    )
    Row(
      modifier = Modifier
        .clickable(onClick = onDelete)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Filled.Delete,
        contentDescription = null,
        tint = Color(0xFFDC2626),
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = deleteLabel,
        color = Color(0xFFDC2626),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

/**
 * The avatar, or the space it would take.
 *
 * Mid-run the slot stays empty rather than collapsing: without it the bubbles
 * of a burst would each sit at a different distance from the edge. With no photo
 * the package fallback still reserves the same width, so the run stays aligned.
 */
@Composable
private fun BubbleAvatarSlot(isLastInGroup: Boolean, url: String?, accent: Color) {
  if (isLastInGroup) {
    ProjectAvatar(logoUrl = url, accent = accent, size = SupportTokens.avatarSize)
  } else {
    Box(modifier = Modifier.size(SupportTokens.avatarSize))
  }
}

/**
 * Bubble with reaction chip overlapping the bottom corner (iOS MessageBubble
 * parity / Figma bubble-reaction).
 */
@Composable
private fun BubbleWithReactions(
  body: String,
  images: List<Attachment>,
  config: MessengerConfig,
  fromStudio: Boolean,
  reactions: List<MessageReaction>,
  resolveAttachmentUrl: suspend (Attachment) -> String,
  onToggleReaction: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasReactions = reactions.isNotEmpty()
  // Reserve only the overhang so the chip can sit on the bubble edge
  // without stretching the thread like a second row.
  Box(
    modifier = modifier.padding(bottom = if (hasReactions) 6.dp else 0.dp),
    contentAlignment = if (fromStudio) Alignment.BottomStart else Alignment.BottomEnd,
  ) {
    BubbleBody(
      body = body,
      images = images,
      config = config,
      fromStudio = fromStudio,
      resolveAttachmentUrl = resolveAttachmentUrl,
    )
    if (hasReactions) {
      ReactionChip(
        reactions = reactions,
        onToggle = onToggleReaction,
        modifier = Modifier.offset(y = 6.dp),
      )
    }
  }
}

@Composable
private fun ReactionChip(
  reactions: List<MessageReaction>,
  onToggle: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val sorted = reactions.sortedWith(
    compareByDescending<MessageReaction> { it.count }
      .thenBy { it.emoji },
  )
  val visible = if (sorted.size <= 2) sorted else sorted.take(2)
  val extra = (sorted.size - visible.size).coerceAtLeast(0)
  val shape = RoundedCornerShape(999.dp)

  // Material's 48dp min touch target would inflate the chip; keep it visual-sized.
  CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Row(
      modifier = modifier
        .wrapContentSize()
        .border(1.dp, SupportTokens.surface, shape)
        .clip(shape)
        .background(SupportTokens.surface)
        .padding(horizontal = 3.dp, vertical = 1.dp),
      horizontalArrangement = Arrangement.spacedBy(1.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      visible.forEach { reaction ->
        val interaction = remember(reaction.emoji) { MutableInteractionSource() }
        Text(
          text = reaction.emoji,
          fontSize = 11.sp,
          lineHeight = 11.sp,
          modifier = Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = { onToggle(reaction.emoji) },
          ),
        )
      }
      if (extra > 0) {
        Text(
          text = "+$extra",
          fontSize = 9.sp,
          fontWeight = FontWeight.SemiBold,
          lineHeight = 9.sp,
          color = SupportTokens.textMain,
        )
      }
    }
  }
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
  body: String,
  images: List<Attachment>,
  config: MessengerConfig,
  fromStudio: Boolean,
  resolveAttachmentUrl: suspend (Attachment) -> String,
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
    images.forEach { attachment ->
      ImageAttachmentPreview(
        attachment = attachment,
        resolveUrl = resolveAttachmentUrl,
      )
    }
    if (body.isNotBlank()) {
      Text(
        text = body,
        fontSize = SupportTokens.bodyText,
        fontWeight = FontWeight.Medium,
        lineHeight = SupportTokens.bodyText * 1.4f,
        color = if (fromStudio) SupportTokens.textMain else config.onAccentColor,
      )
    }
  }
}

/**
 * Non-image, non-video attachment card (PDF, zip…), matching dashboard / iOS
 * `FileBubble`: muted doc tile, filename, external-link affordance.
 */
@Composable
private fun FileAttachmentCard(
  attachment: Attachment,
  strings: SupportStrings,
  onOpen: (Attachment) -> Unit,
) {
  val openLabel = strings.openFile(attachment.filename)
  val icon = SupportIcons.Document
  Row(
    modifier = Modifier
      .widthIn(min = 200.dp, max = 280.dp)
      .semantics { contentDescription = openLabel }
      .clip(RoundedCornerShape(16.dp))
      .border(1.dp, SupportTokens.border, RoundedCornerShape(16.dp))
      .background(SupportTokens.surface)
      .clickable { onOpen(attachment) }
      .padding(6.dp)
      .padding(end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier
        .size(32.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(SupportTokens.surfaceMuted),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        icon,
        contentDescription = null,
        tint = SupportTokens.textMain,
        modifier = Modifier.size(16.dp),
      )
    }
    Text(
      text = attachment.filename,
      fontSize = SupportTokens.captionText,
      fontWeight = FontWeight.Medium,
      color = SupportTokens.textMain,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Icon(
      SupportIcons.ExternalLink,
      contentDescription = null,
      tint = SupportTokens.textTertiary,
      modifier = Modifier.size(14.dp),
    )
  }
}

@Composable
private fun BubbleFooter(
  message: Message,
  strings: SupportStrings,
  language: String? = null,
  fromStudio: Boolean,
  hasAvatar: Boolean,
  hasTranslation: Boolean = false,
  showsOriginal: Boolean = false,
  onToggleOriginal: () -> Unit = {},
) {
  val context = LocalContext.current
  val stamp = buildString {
    // Absolute short time in the customer's recognised language.
    append(formatShortTime(context, message.createdAtMillis, language))
    // The read receipt only shows on our own messages: on the studio's, it
    // tells the user nothing.
    if (!fromStudio && message.readAtMillis != null) append(" - ").append(strings.seen)
  }
  if (stamp.isBlank() && !hasTranslation) return

  // Cleared past the avatar on its own side, so the stamp lines up with the
  // bubble's edge rather than the avatar's. No avatar, nothing to clear.
  val indent =
    if (hasAvatar) SupportTokens.avatarSize + SupportTokens.bubbleGap else 4.dp
  Row(
    modifier = Modifier.padding(
      top = 4.dp,
      start = if (fromStudio) indent else 4.dp,
      end = if (fromStudio) 4.dp else indent,
    ),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stamp,
      fontSize = SupportTokens.captionText,
      color = SupportTokens.textTertiary,
    )
    if (hasTranslation) {
      Text(
        text = "·",
        fontSize = SupportTokens.captionText,
        color = SupportTokens.textTertiary,
      )
      Text(
        text = if (showsOriginal) strings.seeTranslation else strings.showOriginal,
        fontSize = SupportTokens.captionText,
        color = SupportTokens.textTertiary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onToggleOriginal),
      )
    }
  }
}

/* -------------------------------------------------------------------------- */
/* Config helpers                                                             */
/* -------------------------------------------------------------------------- */

/** Studio agent name for the thread header - same chain as [headerTitle]. */
internal fun MessengerConfig.agentTitle(strings: SupportStrings): String = headerTitle(strings)

/** Studio agent avatar, falling back to the project logo. */
internal val MessengerConfig.agentAvatar: String?
  get() = messaging.agentAvatarUrl ?: context.agentAvatarUrl ?: context.projectLogoUrl
