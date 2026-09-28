package io.appwin.support.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import io.appwin.support.domain.Conversation
import io.appwin.support.domain.ConversationStatus
import io.appwin.support.domain.FaqGroup
import io.appwin.support.domain.MessageAuthorType
import io.appwin.support.domain.MessengerBannerSource
import io.appwin.support.domain.MessengerConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.text.format.DateFormat as AndroidDateFormat

/**
 * Root of the messenger - Figma 441:10486 (home) and 470:6959 (thread).
 *
 * Navigation by hand rather than a library: four screens, in a view the host app
 * can embed. A navigation graph would impose its dependency - and its version
 * conflicts - on every integrating app. Screens swap inside the sheet, they do
 * not push over it: the panel and its header are what the user sees as "the
 * messenger", and replacing them wholesale reads as leaving the app.
 */
@Composable
internal fun MessengerRoot(
  modifier: Modifier = Modifier,
  viewModel: SupportViewModel = viewModel(),
  initialConversationId: String? = null,
  onClose: (() -> Unit)? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val strings = remember(context, state.customerLanguage) {
    supportStrings(context, state.customerLanguage)
  }
  var route by remember { mutableStateOf<MessengerRoute>(MessengerRoute.Home) }

  // Opened from a banner or a push tap: land on the thread, not on home.
  LaunchedEffect(initialConversationId) {
    val id = initialConversationId ?: return@LaunchedEffect
    viewModel.openConversation(id)
    route = MessengerRoute.Thread
  }

  SupportTheme(state.config) {
    Box(
      modifier
        .fillMaxWidth()
        .background(SupportTokens.sheetBackground),
    ) {
      when {
        state.isLoading -> Centered {
          CircularProgressIndicator(color = state.config.accentColor)
        }

        state.loadFailed -> EmptyState(
          title = strings.loadErrorTitle,
          message = strings.loadErrorMessage,
          actionLabel = strings.retry,
          onAction = viewModel::load,
        )

        else -> MessengerContent(
          route = route,
          onRoute = { route = it },
          viewModel = viewModel,
          strings = strings,
          onClose = onClose,
        )
      }
    }
  }
}

@Composable
private fun MessengerContent(
  route: MessengerRoute,
  onRoute: (MessengerRoute) -> Unit,
  viewModel: SupportViewModel,
  strings: SupportStrings,
  onClose: (() -> Unit)?,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val backToHome = {
    if (route == MessengerRoute.Thread) viewModel.leaveConversation()
    onRoute(MessengerRoute.Home)
  }

  BackHandler(enabled = route != MessengerRoute.Home) { backToHome() }

  AnimatedContent(
    targetState = route,
    transitionSpec = {
      // Depth reads left to right: going deeper slides in from the right, going
      // back slides the other way, which matches the system back gesture.
      val forward = targetState.depth >= initialState.depth
      val width = { w: Int -> if (forward) w else -w }
      (slideInHorizontally(initialOffsetX = width) + fadeIn()) togetherWith
        (slideOutHorizontally(targetOffsetX = { -width(it) }) + fadeOut()) using
        SizeTransform(clip = false)
    },
    label = "messenger-route",
  ) { current ->
    when (current) {
      MessengerRoute.Home -> HomeScreen(
        state = state,
        strings = strings,
        onClose = onClose,
        onNewConversation = {
          viewModel.startNewConversation()
          onRoute(MessengerRoute.Thread)
        },
        onOpenInbox = { onRoute(MessengerRoute.Conversations) },
        onOpenConversation = {
          viewModel.openConversation(it.id)
          onRoute(MessengerRoute.Thread)
        },
        onOpenFaq = { onRoute(it) },
      )

      MessengerRoute.Conversations -> ConversationsScreen(
        state = state,
        strings = strings,
        onBack = backToHome,
        onClose = onClose,
        onNewConversation = {
          viewModel.startNewConversation()
          onRoute(MessengerRoute.Thread)
        },
        onOpenConversation = {
          viewModel.openConversation(it.id)
          onRoute(MessengerRoute.Thread)
        },
      )

      MessengerRoute.Thread -> ThreadScreen(
        viewModel = viewModel,
        strings = strings,
        onBack = backToHome,
        onClose = onClose,
      )

      is MessengerRoute.Faq -> FaqArticleScreen(
        config = state.config,
        question = current.question,
        answer = current.answer,
        strings = strings,
        onBack = backToHome,
        onClose = onClose,
      )
    }
  }
}

internal sealed interface MessengerRoute {
  /** Slide direction: a deeper screen enters from the right. */
  val depth: Int

  data object Home : MessengerRoute {
    override val depth: Int get() = 0
  }

  data object Conversations : MessengerRoute {
    override val depth: Int get() = 1
  }

  data object Thread : MessengerRoute {
    override val depth: Int get() = 2
  }

  data class Faq(val question: String, val answer: String) : MessengerRoute {
    override val depth: Int get() = 1
  }
}

/* -------------------------------------------------------------------------- */
/* Home (441:10486)                                                           */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeScreen(
  state: SupportUiState,
  strings: SupportStrings,
  onClose: (() -> Unit)?,
  onNewConversation: () -> Unit,
  onOpenInbox: () -> Unit,
  onOpenConversation: (Conversation) -> Unit,
  onOpenFaq: (MessengerRoute.Faq) -> Unit,
) {
  val config = state.config
  val accent = config.accentColor
  val recent = remember(state.conversations) {
    state.conversations
      .filter { it.status == ConversationStatus.OPEN }
      .maxByOrNull { c ->
        val last = c.lastMessageAtMillis
        if (last != null && last > 0L) last else c.createdAtMillis
      }
  }

  SheetColumn {
    SheetHeader(
      title = config.headerTitle(strings),
      logoUrl = config.agentAvatar,
      accent = accent,
      closeLabel = strings.close,
      onClose = onClose,
    )

    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(SupportTokens.sectionGap),
    ) {
      if (config.design.bannerSource != MessengerBannerSource.NONE) {
        MessengerBanner(design = config.design, accent = accent)
      }

      Text(
        text = strings.greeting,
        fontSize = SupportTokens.greetingText,
        fontWeight = FontWeight.Medium,
        lineHeight = SupportTokens.greetingText * 1.2f,
        color = SupportTokens.textMain,
      )

      if (recent != null) {
        RecentMessageCard(
          conversation = recent,
          agentName = config.agentTitle(strings),
          agentAvatar = config.agentAvatar,
          strings = strings,
          accent = accent,
          radius = config.design.radius.dp.dp,
          onClick = { onOpenConversation(recent) },
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(SupportTokens.itemGap)) {
        AccentActionCard(
          label = strings.newConversation,
          icon = SupportIcons.Send,
          design = config.design,
          accent = accent,
          onAccent = config.onAccentColor,
          onClick = onNewConversation,
        )

        PlainActionCard(
          label = strings.conversations,
          icon = SupportIcons.Inbox,
          radius = config.design.radius.dp.dp,
          showBadge = state.conversations.any { it.hasUnread },
          accent = accent,
          onClick = onOpenInbox,
        )
      }

      if (config.modules.faqEnabled && state.faqGroups.isNotEmpty()) {
        FaqSection(state.faqGroups, config.design.radius.dp.dp, strings, onOpenFaq)
      }
    }
  }
}

/** Intercom-style home teaser for the latest open conversation. */
@Composable
private fun RecentMessageCard(
  conversation: Conversation,
  agentName: String,
  agentAvatar: String?,
  strings: SupportStrings,
  accent: Color,
  radius: androidx.compose.ui.unit.Dp,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(radius)
  val body = conversationPreviewLabel(conversation.preview, strings)
  val preview =
    if (conversation.lastMessageAuthorType == MessageAuthorType.CUSTOMER) {
      strings.youPreview(body)
    } else {
      body
    }
  val whenLabel = relativeTime(
    millis = conversation.lastMessageAtMillis?.takeIf { it > 0L } ?: conversation.createdAtMillis,
    strings = strings,
  )
  val meta = if (whenLabel.isEmpty()) agentName else "$agentName · $whenLabel"

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .softCardShadow(shape)
      .clip(shape)
      .background(SupportTokens.surface, shape)
      .border(1.dp, SupportTokens.borderSubtle, shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = strings.recentMessage,
      fontSize = 13.sp,
      lineHeight = 13.sp * 1.2f,
      fontWeight = FontWeight.SemiBold,
      color = SupportTokens.textMain,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Padding keeps the unread dot inside the card (iOS RecentMessageCard).
      Box(modifier = Modifier.padding(top = 2.dp, end = 2.dp)) {
        ProjectAvatar(logoUrl = agentAvatar, accent = accent, size = 40.dp)
        if (conversation.hasUnread) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .offset(x = 2.dp, y = (-2).dp)
              .size(10.dp)
              .background(accent, CircleShape)
              .border(2.dp, Color.White, CircleShape),
          )
        }
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(1.dp),
      ) {
        Text(
          text = preview,
          fontSize = 14.sp,
          lineHeight = 14.sp * 1.2f,
          fontWeight = if (conversation.hasUnread) FontWeight.SemiBold else FontWeight.Medium,
          color = SupportTokens.textMain,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = meta,
          fontSize = 12.sp,
          lineHeight = 12.sp * 1.2f,
          fontWeight = FontWeight.Normal,
          color = if (conversation.hasUnread) accent else SupportTokens.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        SupportIcons.ChevronRight,
        contentDescription = null,
        tint = SupportTokens.textTertiary,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

/** Primary call to action - gradient when the studio asked for one. */
@Composable
private fun AccentActionCard(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  design: io.appwin.support.domain.MessengerDesign,
  accent: Color,
  onAccent: Color,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(design.radius.dp.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(accentBrush(design, accent), shape)
      .border(2.dp, Color.White.copy(alpha = 0.2f), shape)
      .clickable(onClick = onClick)
      .padding(SupportTokens.cardPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(icon, contentDescription = null, tint = onAccent, modifier = Modifier.size(SupportTokens.iconSize))
    Text(
      text = label,
      fontSize = SupportTokens.bodyText,
      fontWeight = FontWeight.Medium,
      color = onAccent,
      modifier = Modifier.weight(1f),
    )
  }
}

/** Secondary row - white card with a chevron. */
@Composable
private fun PlainActionCard(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  radius: androidx.compose.ui.unit.Dp,
  onClick: () -> Unit,
  showBadge: Boolean = false,
  accent: Color = Color(0xFFF97316),
) {
  val shape = RoundedCornerShape(radius)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(SupportTokens.surface, shape)
      .border(1.dp, SupportTokens.borderSubtle, shape)
      .clickable(onClick = onClick)
      .padding(SupportTokens.cardPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Unread dot on the icon (iOS ConversationsCard), not a free-floating chip.
    Box {
      Icon(
        icon,
        contentDescription = null,
        tint = SupportTokens.textMain,
        modifier = Modifier.size(SupportTokens.iconSize),
      )
      if (showBadge) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 3.dp, y = (-3).dp)
            .size(8.dp)
            .background(accent, CircleShape),
        )
      }
    }
    Text(
      text = label,
      fontSize = SupportTokens.bodyText,
      fontWeight = FontWeight.Medium,
      color = SupportTokens.textMain,
      modifier = Modifier.weight(1f),
    )
    Icon(
      SupportIcons.ChevronRight,
      contentDescription = null,
      tint = SupportTokens.textMain,
      modifier = Modifier.size(SupportTokens.smallIconSize),
    )
  }
}

@Composable
private fun FaqSection(
  groups: List<FaqGroup>,
  radius: androidx.compose.ui.unit.Dp,
  strings: SupportStrings,
  onOpenFaq: (MessengerRoute.Faq) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(SupportTokens.itemGap)) {
    Text(
      text = strings.faq,
      fontSize = SupportTokens.bodyText,
      fontWeight = FontWeight.Medium,
      color = SupportTokens.textSecondary,
    )
    groups.forEach { group ->
      group.articles.forEach { article ->
        PlainActionCard(
          label = article.question,
          icon = Icons.Default.Info,
          radius = radius,
          onClick = { onOpenFaq(MessengerRoute.Faq(article.question, article.answer)) },
        )
      }
    }
  }
}

/* -------------------------------------------------------------------------- */
/* Conversations list - iOS ConversationList / ConversationRow parity         */
/* -------------------------------------------------------------------------- */

private val InboxAvatarSize = 44.dp

@Composable
private fun ConversationsScreen(
  state: SupportUiState,
  strings: SupportStrings,
  onBack: () -> Unit,
  onClose: (() -> Unit)?,
  onNewConversation: () -> Unit,
  onOpenConversation: (Conversation) -> Unit,
) {
  val config = state.config
  val radius = config.design.radius.dp.dp
  val accent = config.accentColor
  val onAccent = config.onAccentColor
  val agentName = config.agentTitle(strings)

  SheetColumn {
    SheetHeader(
      title = strings.conversationsTitle,
      logoUrl = config.agentAvatar,
      accent = accent,
      closeLabel = strings.close,
      onBack = onBack,
      onClose = onClose,
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = true)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(SupportTokens.itemGap),
    ) {
      InboxNewConversationCta(
        label = strings.newConversationPreview,
        design = config.design,
        accent = accent,
        onAccent = onAccent,
        onClick = onNewConversation,
      )

      if (state.conversations.isEmpty()) {
        InboxEmptyState(
          strings = strings,
          accent = accent,
          onAccent = onAccent,
          design = config.design,
          onWrite = onNewConversation,
        )
      } else {
        state.conversations.forEach { conversation ->
          ConversationCard(
            conversation = conversation,
            agentName = agentName,
            agentAvatar = config.agentAvatar,
            strings = strings,
            accent = accent,
            radius = radius,
            language = state.customerLanguage,
            onClick = { onOpenConversation(conversation) },
          )
        }
      }
    }
  }
}

@Composable
private fun InboxNewConversationCta(
  label: String,
  design: io.appwin.support.domain.MessengerDesign,
  accent: Color,
  onAccent: Color,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(design.radius.dp.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(52.dp)
      .clip(shape)
      .background(accentBrush(design, accent), shape)
      .border(1.5.dp, Color.White.copy(alpha = 0.2f), shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      SupportIcons.Pen,
      contentDescription = null,
      tint = onAccent,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = onAccent,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun InboxEmptyState(
  strings: SupportStrings,
  accent: Color,
  onAccent: Color,
  design: io.appwin.support.domain.MessengerDesign,
  onWrite: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 48.dp, horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(
      modifier = Modifier
        .shadow(4.dp, CircleShape, clip = false)
        .size(72.dp)
        .clip(CircleShape)
        .background(SupportTokens.surface),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        SupportIcons.Inbox,
        contentDescription = null,
        tint = SupportTokens.textTertiary,
        modifier = Modifier.size(32.dp),
      )
    }
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = strings.noConversation,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = SupportTokens.textMain,
        textAlign = TextAlign.Center,
      )
      Text(
        text = strings.emptyConversationsHint,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = SupportTokens.textSecondary,
        textAlign = TextAlign.Center,
      )
    }
    Text(
      text = strings.writeMessage,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
      color = onAccent,
      modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(accentBrush(design, accent))
        .clickable(onClick = onWrite)
        .padding(horizontal = 20.dp, vertical = 12.dp),
    )
  }
}

@Composable
private fun ConversationCard(
  conversation: Conversation,
  agentName: String,
  agentAvatar: String?,
  strings: SupportStrings,
  accent: Color,
  radius: androidx.compose.ui.unit.Dp,
  language: String? = null,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(radius)
  val unread = conversation.hasUnread
  val preview = conversationPreviewLabel(conversation.preview, strings)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .softCardShadow(shape)
      .clip(shape)
      .background(SupportTokens.surface, shape)
      .border(1.dp, SupportTokens.borderSubtle, shape)
      .clickable(onClick = onClick)
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(modifier = Modifier.padding(top = 2.dp, end = 2.dp)) {
      ProjectAvatar(logoUrl = agentAvatar, accent = accent, size = InboxAvatarSize)
      if (unread) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 2.dp, y = (-2).dp)
            .size(10.dp)
            .background(accent, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        )
      }
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = agentName,
          fontSize = 14.sp,
          fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
          color = SupportTokens.textMain,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = inboxRelativeTime(
            context = LocalContext.current,
            millis = conversation.lastMessageAtMillis ?: conversation.createdAtMillis,
            strings = strings,
            language = language,
          ),
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = if (unread) accent else SupportTokens.textTertiary,
          maxLines = 1,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = preview,
          fontSize = 13.sp,
          fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
          color = if (unread) SupportTokens.textMain else SupportTokens.textSecondary,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Icon(
          SupportIcons.ChevronRight,
          contentDescription = null,
          tint = SupportTokens.textTertiary,
          modifier = Modifier.size(14.dp),
        )
      }

      if (conversation.status == ConversationStatus.RESOLVED ||
        conversation.status == ConversationStatus.CLOSED
      ) {
        Text(
          text = when (conversation.status) {
            ConversationStatus.RESOLVED -> strings.statusResolved
            else -> strings.statusClosed
          },
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = SupportTokens.textSecondary,
          modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(SupportTokens.surfaceMuted)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
    }
  }
}

/** Preview line: media filename → Photo/Video/File, else text or empty label. */
private fun conversationPreviewLabel(raw: String?, strings: SupportStrings): String {
  val t = raw?.trim().orEmpty()
  if (t.isEmpty()) return strings.newConversationPreview
  mediaPreviewLabel(t, strings)?.let { return it }
  return t
}

private fun mediaPreviewLabel(s: String, strings: SupportStrings): String? {
  if (s.contains(' ')) return null
  val dot = s.lastIndexOf('.')
  if (dot <= 0 || dot == s.lastIndex) return null
  val ext = s.substring(dot + 1).lowercase(Locale.getDefault())
  if (ext.length !in 1..5 || !ext.all { it.isLetterOrDigit() }) return null
  return when (ext) {
    "jpg", "jpeg", "png", "gif", "webp", "heic" -> strings.mediaPhoto
    "mp4", "mov", "m4v", "webm" -> strings.mediaVideo
    "pdf" -> strings.mediaPdf
    else -> strings.mediaFile
  }
}

/** Inbox timestamps: today = short time, yesterday, else `d MMM` (iOS ConversationRow). */
private fun inboxRelativeTime(
  context: Context,
  millis: Long,
  strings: SupportStrings,
  language: String? = null,
): String {
  if (millis <= 0L) return ""
  val zone = deviceTimeZone()
  val cal = Calendar.getInstance(zone).apply { timeInMillis = millis }
  val now = Calendar.getInstance(zone)
  if (sameCalendarDay(cal, now)) {
    return formatShortTime(context, millis, language)
  }
  val yesterday = Calendar.getInstance(zone).apply { add(Calendar.DAY_OF_YEAR, -1) }
  if (sameCalendarDay(cal, yesterday)) return strings.yesterday
  val locale = displayLocale(language, context)
  val pattern = if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "d MMM" else "d MMM yyyy"
  return SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(millis))
}

/**
 * Short clock for message stamps.
 *
 * Follows the **customer's** locale hour cycle (like iOS `DateFormatter.timeStyle
 * = .short`), not the phone's 12/24 toggle alone: a FR customer on a US 12h
 * device must see `11:20`, not `11:20 AM`. Formats in the **device** timezone
 * from `persist.sys.timezone` (see [deviceTimeZone]): React Native / Hermes
 * often leave `TimeZone.getDefault()` on UTC.
 *
 * Do not probe `DateFormat.getTimeInstance` to detect AmPm: on Android that
 * pattern is rewritten from the system 12h setting, so FR looks like en-US.
 */
internal fun formatShortTime(context: Context, millis: Long, language: String? = null): String {
  if (millis <= 0L) return ""
  val locale = displayLocale(language, context)
  val force24 = !localeUsesAmPmClock(locale) || AndroidDateFormat.is24HourFormat(context)
  val zone = deviceTimeZone()
  val cal = Calendar.getInstance(zone).apply { timeInMillis = millis }
  if (force24) {
    return String.format(
      locale,
      "%02d:%02d",
      cal.get(Calendar.HOUR_OF_DAY),
      cal.get(Calendar.MINUTE),
    )
  }
  val pattern = AndroidDateFormat.getBestDateTimePattern(locale, "hm")
  return SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(millis))
}

/**
 * Whether this locale's CLDR short time uses a 12-hour clock.
 *
 * Ignores the device 12/24 preference (unlike `DateFormat.getTimeInstance` /
 * skeleton `j` on Android, which rewrite FR to AmPm on a US phone).
 *
 * Heuristic rather than ICU `defaultHourCycle`: that API is 33+ and the product
 * audience is mostly 24h locales; en-US/CA remain 12h unless the device is 24h.
 */
private fun localeUsesAmPmClock(locale: Locale): Boolean {
  val lang = locale.language.lowercase(Locale.ROOT)
  if (lang != "en") return false
  val region = android.icu.util.ULocale.addLikelySubtags(
    android.icu.util.ULocale.forLocale(locale),
  ).country.uppercase(Locale.ROOT)
  return region !in setOf("GB", "IE")
}

/**
 * Wall-clock zone from the system property.
 *
 * Do not use [android.text.format.Time.getCurrentTimezone]: on current Android
 * it is just `TimeZone.getDefault().id`, and the JVM default is often UTC
 * inside a React Native process.
 */
internal fun deviceTimeZone(): TimeZone {
  val id = systemTimezoneId()
  if (!id.isNullOrBlank()) return TimeZone.getTimeZone(id)
  return TimeZone.getDefault()
}

private fun systemTimezoneId(): String? =
  try {
    val clazz = Class.forName("android.os.SystemProperties")
    val get = clazz.getMethod("get", String::class.java, String::class.java)
    (get.invoke(null, "persist.sys.timezone", "") as? String)?.trim()?.takeIf { it.isNotEmpty() }
  } catch (_: Throwable) {
    null
  }

internal fun appLocale(context: Context): Locale {
  val locales = context.resources.configuration.locales
  return if (locales.size() > 0) locales[0] else Locale.getDefault()
}

internal fun sameCalendarDay(a: java.util.Calendar, b: java.util.Calendar): Boolean =
  a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
    a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

/* -------------------------------------------------------------------------- */
/* FAQ article                                                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun FaqArticleScreen(
  config: MessengerConfig,
  question: String,
  answer: String,
  strings: SupportStrings,
  onBack: () -> Unit,
  onClose: (() -> Unit)?,
) {
  SheetColumn {
    SheetHeader(
      title = strings.faq,
      logoUrl = config.agentAvatar,
      accent = config.accentColor,
      closeLabel = strings.close,
      onBack = onBack,
      onClose = onClose,
    )
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(SupportTokens.itemGap),
    ) {
      Text(
        text = question,
        fontSize = SupportTokens.greetingText,
        fontWeight = FontWeight.Medium,
        lineHeight = SupportTokens.greetingText * 1.2f,
        color = SupportTokens.textMain,
      )
      Text(
        text = answer,
        fontSize = SupportTokens.bodyText,
        color = SupportTokens.textSecondary,
        lineHeight = SupportTokens.bodyText * 1.5f,
      )
    }
  }
}

/* -------------------------------------------------------------------------- */
/* Shared                                                                     */
/* -------------------------------------------------------------------------- */

/** Sheet padding and section rhythm, shared by every screen. */
@Composable
internal fun SheetColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      // The sheet zeroes its own insets to reach the top, so each screen clears
      // the navigation bar itself.
      .navigationBarsPadding()
      .padding(SupportTokens.sheetPadding),
    verticalArrangement = Arrangement.spacedBy(SupportTokens.sectionGap),
    content = content,
  )
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun EmptyState(
  title: String,
  message: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = title,
      fontSize = SupportTokens.bodyText,
      fontWeight = FontWeight.Medium,
      color = SupportTokens.textMain,
      textAlign = TextAlign.Center,
    )
    if (message.isNotBlank()) {
      Text(
        text = message,
        fontSize = SupportTokens.captionText,
        color = SupportTokens.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
      )
    }
    if (actionLabel != null && onAction != null) {
      Text(
        text = actionLabel,
        fontSize = SupportTokens.bodyText,
        fontWeight = FontWeight.Medium,
        color = SupportTokens.textSecondary,
        modifier = Modifier.padding(top = 12.dp).clickable(onClick = onAction),
      )
    }
  }
}

/** Relative age, worded from the device locale via [SupportStrings]. */
internal fun relativeTime(
  millis: Long,
  strings: SupportStrings,
  nowMillis: Long = System.currentTimeMillis(),
): String {
  if (millis <= 0) return ""
  val seconds = ((nowMillis - millis) / 1000).coerceAtLeast(0)
  return when {
    seconds < 60 -> strings.relativeNow()
    seconds < 3_600 -> strings.relativeMinutes((seconds / 60).toInt())
    seconds < 86_400 -> strings.relativeHours((seconds / 3_600).toInt())
    seconds < 604_800 -> strings.relativeDays((seconds / 86_400).toInt())
    else -> strings.relativeWeeks((seconds / 604_800).toInt())
  }
}
