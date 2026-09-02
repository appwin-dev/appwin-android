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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appwin.support.domain.Conversation
import io.appwin.support.domain.FaqGroup
import io.appwin.support.domain.MessengerConfig

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
  onClose: (() -> Unit)? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val strings = remember(context) { SupportStrings(context) }
  var route by remember { mutableStateOf<MessengerRoute>(MessengerRoute.Home) }

  SupportTheme(state.config) {
    Box(
      modifier
        .fillMaxWidth()
        .background(SupportTokens.sheetBackground),
    ) {
      when {
        state.isLoading -> Centered { CircularProgressIndicator() }

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
  val backToHome = { onRoute(MessengerRoute.Home) }

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
        onOpenFaq = { onRoute(it) },
      )

      MessengerRoute.Conversations -> ConversationsScreen(
        state = state,
        strings = strings,
        onBack = backToHome,
        onClose = onClose,
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
  onOpenFaq: (MessengerRoute.Faq) -> Unit,
) {
  val config = state.config
  val accent = config.accentColor

  SheetColumn {
    SheetHeader(
      title = config.headerTitle(strings),
      logoUrl = config.context.projectLogoUrl,
      accent = accent,
      closeLabel = strings.close,
      onClose = onClose,
    )

    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(SupportTokens.sectionGap),
    ) {
      MessengerBanner(design = config.design, accent = accent)

      Text(
        text = strings.greeting,
        fontSize = SupportTokens.greetingText,
        fontWeight = FontWeight.Medium,
        lineHeight = SupportTokens.greetingText * 1.2f,
        color = SupportTokens.textMain,
      )

      Column(verticalArrangement = Arrangement.spacedBy(SupportTokens.itemGap)) {
        AccentActionCard(
          label = strings.newConversation,
          icon = Icons.Default.Send,
          design = config.design,
          accent = accent,
          onAccent = config.onAccentColor,
          onClick = onNewConversation,
        )

        PlainActionCard(
          label = strings.conversations,
          icon = Icons.Default.Email,
          radius = config.design.radius.dp.dp,
          onClick = onOpenInbox,
        )
      }

      if (config.modules.faqEnabled && state.faqGroups.isNotEmpty()) {
        FaqSection(state.faqGroups, config.design.radius.dp.dp, strings, onOpenFaq)
      }
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
    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = SupportTokens.textMain,
      modifier = Modifier.size(SupportTokens.iconSize),
    )
    Text(
      text = label,
      fontSize = SupportTokens.bodyText,
      fontWeight = FontWeight.Medium,
      color = SupportTokens.textMain,
      modifier = Modifier.weight(1f),
    )
    Icon(
      Icons.Default.KeyboardArrowRight,
      contentDescription = null,
      tint = SupportTokens.textTertiary,
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
/* Conversations list                                                         */
/* -------------------------------------------------------------------------- */

@Composable
private fun ConversationsScreen(
  state: SupportUiState,
  strings: SupportStrings,
  onBack: () -> Unit,
  onClose: (() -> Unit)?,
  onOpenConversation: (Conversation) -> Unit,
) {
  val config = state.config
  SheetColumn {
    SheetHeader(
      title = strings.conversations,
      logoUrl = config.context.projectLogoUrl,
      accent = config.accentColor,
      closeLabel = strings.close,
      onBack = onBack,
      onClose = onClose,
    )

    if (state.conversations.isEmpty()) {
      EmptyState(title = strings.noConversation, message = "")
    } else {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SupportTokens.itemGap),
      ) {
        state.conversations.forEach { conversation ->
          ConversationCard(
            conversation = conversation,
            strings = strings,
            accent = config.accentColor,
            radius = config.design.radius.dp.dp,
            onClick = { onOpenConversation(conversation) },
          )
        }
      }
    }
  }
}

@Composable
private fun ConversationCard(
  conversation: Conversation,
  strings: SupportStrings,
  accent: Color,
  radius: androidx.compose.ui.unit.Dp,
  onClick: () -> Unit,
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
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = conversation.preview.orEmpty().ifBlank { strings.newConversation },
        fontSize = SupportTokens.bodyText,
        fontWeight = if (conversation.hasUnread) FontWeight.SemiBold else FontWeight.Medium,
        color = SupportTokens.textMain,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = conversation.subtitle(strings),
        fontSize = SupportTokens.captionText,
        color = SupportTokens.textTertiary,
      )
    }
    if (conversation.hasUnread) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
    }
  }
}

private fun Conversation.subtitle(strings: SupportStrings): String {
  val status = when (status) {
    io.appwin.support.domain.ConversationStatus.RESOLVED -> strings.statusResolved
    io.appwin.support.domain.ConversationStatus.CLOSED -> strings.statusClosed
    else -> ""
  }
  val age = relativeTime(lastMessageAtMillis ?: createdAtMillis)
  return listOf(status, age).filter { it.isNotEmpty() }.joinToString(" - ")
}

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
      logoUrl = config.context.projectLogoUrl,
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

/** Relative age, worded consistently across Android versions. */
internal fun relativeTime(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
  if (millis <= 0) return ""
  val seconds = ((nowMillis - millis) / 1000).coerceAtLeast(0)
  return when {
    seconds < 60 -> "now"
    seconds < 3_600 -> "${seconds / 60}m"
    seconds < 86_400 -> "${seconds / 3_600}h"
    seconds < 604_800 -> "${seconds / 86_400}d"
    else -> "${seconds / 604_800}w"
  }
}
