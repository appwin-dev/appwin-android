package io.appwin.support.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.appwin.support.domain.Conversation
import io.appwin.support.domain.FaqGroup
import io.appwin.support.domain.Message

/**
 * Racine du messenger : accueil, fil de conversation, article de FAQ.
 *
 * Navigation by hand rather than a library: three screens, in a view the host
 * app can embed. A navigation graph would impose its dependency - and its
 * version conflicts - on every integrating app.
 */
@Composable
internal fun MessengerRoot(
  viewModel: SupportViewModel = viewModel(),
  onClose: (() -> Unit)? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val strings = remember(context) { SupportStrings(context) }
  var route by remember { mutableStateOf<MessengerRoute>(MessengerRoute.Home) }

  SupportTheme(state.config) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      when {
        state.isLoading -> Centered { CircularProgressIndicator() }

        state.loadFailed -> EmptyState(
          title = strings.loadErrorTitle,
          message = strings.loadErrorMessage,
          actionLabel = strings.retry,
          onAction = viewModel::load,
        )

        else -> when (val current = route) {
          MessengerRoute.Home -> HomeScreen(
            viewModel = viewModel,
            strings = strings,
            onClose = onClose,
            onOpenConversation = {
              viewModel.openConversation(it.id)
              route = MessengerRoute.Thread
            },
            onNewConversation = {
              viewModel.startNewConversation()
              route = MessengerRoute.Thread
            },
            onOpenFaq = { route = it },
          )

          MessengerRoute.Thread -> ThreadScreen(
            viewModel = viewModel,
            strings = strings,
            onBack = { route = MessengerRoute.Home },
          )

          is MessengerRoute.Faq -> FaqArticleScreen(
            question = current.question,
            answer = current.answer,
            onBack = { route = MessengerRoute.Home },
          )
        }
      }
    }
  }
}

private sealed interface MessengerRoute {
  data object Home : MessengerRoute
  data object Thread : MessengerRoute
  data class Faq(val question: String, val answer: String) : MessengerRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
  viewModel: SupportViewModel,
  strings: SupportStrings,
  onClose: (() -> Unit)?,
  onOpenConversation: (Conversation) -> Unit,
  onNewConversation: () -> Unit,
  onOpenFaq: (MessengerRoute.Faq) -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val config = state.config

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(config.context.projectName.ifBlank { strings.title })
        },
        navigationIcon = {
          if (onClose != null) {
            IconButton(onClick = onClose) {
              Icon(Icons.Default.Close, contentDescription = strings.close)
            }
          }
        },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onNewConversation,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(strings.newConversation) },
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
    ) {
      config.design.bannerUrl?.let { banner ->
        item {
          AsyncImage(
            model = banner,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .size(140.dp)
              .background(MaterialTheme.colorScheme.surfaceVariant),
          )
        }
      }

      // The welcome message sets the tone and frames expectations. It only shows
      // when the studio has enabled it: a generic default would be worse than
      // nothing.
      if (config.messaging.welcomeMessageEnabled) {
        config.messaging.welcomeMessage?.let { welcome ->
          item {
            Text(
              text = welcome,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
          }
        }
      }

      if (state.conversations.isNotEmpty()) {
        item { SectionTitle(strings.conversations) }
        items(state.conversations, key = { it.id }) { conversation ->
          ConversationRow(conversation, strings) { onOpenConversation(conversation) }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
      }

      if (state.faqGroups.isNotEmpty()) {
        item { SectionTitle(strings.faq) }
        state.faqGroups.forEach { group -> faqGroup(group, onOpenFaq) }
      }

      if (state.conversations.isEmpty() && state.faqGroups.isEmpty()) {
        item {
          Text(
            text = strings.noConversation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
          )
        }
      }
    }
  }
}

private fun androidx.compose.foundation.lazy.LazyListScope.faqGroup(
  group: FaqGroup,
  onOpenFaq: (MessengerRoute.Faq) -> Unit,
) {
  item(key = "cat-${group.category.id}") {
    Text(
      text = group.category.name,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
  }
  items(group.articles, key = { it.id }) { article ->
    Text(
      text = article.question,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onOpenFaq(MessengerRoute.Faq(article.question, article.answer)) }
        .padding(horizontal = 20.dp, vertical = 14.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

@Composable
private fun ConversationRow(
  conversation: Conversation,
  strings: SupportStrings,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        text = conversation.preview.orEmpty().ifBlank { strings.newConversation },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (conversation.hasUnread) FontWeight.SemiBold else FontWeight.Normal,
      )
      val status = when (conversation.status) {
        io.appwin.support.domain.ConversationStatus.RESOLVED -> strings.statusResolved
        io.appwin.support.domain.ConversationStatus.CLOSED -> strings.statusClosed
        else -> ""
      }
      val when0 = relativeTime(conversation.lastMessageAtMillis ?: conversation.createdAtMillis)
      Text(
        text = listOf(status, when0).filter { it.isNotEmpty() }.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (conversation.hasUnread) {
      Box(
        Modifier
          .size(9.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadScreen(
  viewModel: SupportViewModel,
  strings: SupportStrings,
  onBack: () -> Unit,
) {
  val thread by viewModel.thread.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  var draft by remember { mutableStateOf("") }

  // A thread reads from the bottom: follow every message received or sent.
  LaunchedEffect(thread.messages.size) {
    if (thread.messages.isNotEmpty()) listState.animateScrollToItem(thread.messages.lastIndex)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            state.config.messaging.agentName
              ?: state.config.context.agentName.ifBlank { strings.title },
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
          }
        },
      )
    },
    bottomBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          placeholder = { Text(strings.messagePlaceholder) },
          modifier = Modifier.weight(1f),
          maxLines = 5,
        )
        IconButton(
          onClick = {
            viewModel.sendMessage(draft) { draft = "" }
            draft = ""
          },
          enabled = draft.isNotBlank() && !thread.sending,
        ) {
          if (thread.sending) {
            CircularProgressIndicator(Modifier.size(20.dp))
          } else {
            Icon(Icons.Default.Send, contentDescription = strings.send)
          }
        }
      }
    },
  ) { padding ->
    when {
      thread.isLoading -> Centered { CircularProgressIndicator() }

      thread.messages.isEmpty() -> EmptyState(
        title = strings.newConversation,
        message = state.config.messaging.welcomeMessage.orEmpty(),
      )

      else -> LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(thread.messages, key = { it.id }) { message ->
          MessageBubble(message, strings)
        }
      }
    }
  }
}

@Composable
private fun MessageBubble(message: Message, strings: SupportStrings) {
  val fromStudio = message.authorType.isStudio
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (fromStudio) Alignment.Start else Alignment.End,
  ) {
    if (fromStudio && !message.authorName.isNullOrBlank()) {
      Text(
        text = message.authorName!!,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
      )
    }

    Column(
      modifier = Modifier
        .widthIn(max = 300.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(
          if (fromStudio) MaterialTheme.colorScheme.surfaceVariant
          else MaterialTheme.colorScheme.primary,
        )
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      message.attachments.filter { it.isImage }.forEach { attachment ->
        AsyncImage(
          model = attachment.url,
          contentDescription = attachment.filename,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxWidth().size(180.dp).clip(MaterialTheme.shapes.small),
        )
      }
      if (message.body.isNotBlank()) {
        Text(
          text = message.body,
          style = MaterialTheme.typography.bodyMedium,
          color = if (fromStudio) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onPrimary,
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
    ) {
      message.reactions.forEach { reaction ->
        Text(
          text = "${reaction.emoji} ${reaction.count}",
          style = MaterialTheme.typography.labelSmall,
        )
      }
      Text(
        text = buildString {
          append(relativeTime(message.createdAtMillis))
          // The read receipt only shows on our own messages: on the studio's, it
          // tells the user nothing.
          if (!fromStudio && message.readAtMillis != null) append(" · ").append(strings.seen)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaqArticleScreen(question: String, answer: String, onBack: () -> Unit) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(question, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(question, style = MaterialTheme.typography.titleMedium)
      Text(answer, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
  )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EmptyState(
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
    Icon(
      Icons.Default.Info,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(40.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 16.dp),
    )
    if (message.isNotBlank()) {
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
      )
    }
    if (actionLabel != null && onAction != null) {
      Text(
        text = actionLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
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
