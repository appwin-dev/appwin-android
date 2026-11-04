package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.data.CommunityActivity
import io.appwin.community.domain.CommunityNotification
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/**
 * The bell's inbox: unread sanctions on top, each waiting for its « Got it »,
 * then what other members did around the member's content, newest first.
 * Opening it reads the activity, which clears the tab's dot.
 */
@Composable
internal fun NotificationsScreen(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onBack: () -> Unit,
  /** Post id, and a reply's root comment to open its thread. */
  onOpen: (postId: String, threadCommentId: String?) -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var sanctions by remember { mutableStateOf<List<CommunityNotification>>(emptyList()) }
  var activity by remember { mutableStateOf<List<CommunityActivity>?>(null) }
  var loadFailed by remember { mutableStateOf(false) }
  var reachedEnd by remember { mutableStateOf(false) }
  var loadingMore by remember { mutableStateOf(false) }
  var attempt by remember { mutableStateOf(0) }

  // Lists first, then marks read: the entries of this visit still show as new.
  LaunchedEffect(attempt) {
    loadFailed = false
    val (loadedSanctions, loadedActivity) = coroutineScope {
      val s = async { runCatching { viewModel.moderation.sanctions() }.getOrNull() }
      val a = async { runCatching { viewModel.inbox.activity(PAGE_SIZE, 0) }.getOrNull() }
      s.await() to a.await()
    }
    if (loadedSanctions != null) {
      sanctions = loadedSanctions.filterNot { it.isRead }
      viewModel.setUnreadSanctionCount(sanctions.size)
    }
    if (loadedActivity == null) {
      loadFailed = true
      activity = activity ?: emptyList()
      return@LaunchedEffect
    }
    activity = loadedActivity
    reachedEnd = loadedActivity.size < PAGE_SIZE
    viewModel.markNotificationsSeen()
  }

  Column(modifier = Modifier.fillMaxSize().background(CommunityColors.background)) {
    TextNavBar(
      title = strings.notificationsTitle,
      leadingLabel = strings.back,
      onLeading = onBack,
      containerColor = CommunityColors.background,
    )
    val entries = activity
    when {
      entries == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

      sanctions.isEmpty() && entries.isEmpty() -> CommunityEmptyState(
        icon = SolarModerationIcons.Bell,
        title = if (loadFailed) strings.loadErrorTitle else strings.notificationsEmpty,
        message = if (loadFailed) strings.loadErrorMessage else strings.notificationsEmptyHint,
        actionLabel = if (loadFailed) strings.retry else null,
        onAction = if (loadFailed) ({ attempt++ }) else null,
        modifier = Modifier.weight(1f),
      )

      else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        if (sanctions.isNotEmpty()) {
          item(key = "sanctions") {
            Column(
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
              verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
              sanctions.forEach { sanction ->
                SanctionCard(sanction, state.config.context.projectName, state.profile, strings) {
                  sanctions = sanctions.filterNot { it.id == sanction.id }
                  viewModel.setUnreadSanctionCount(sanctions.size)
                  scope.launch { runCatching { viewModel.moderation.acknowledge(listOf(sanction.id)) } }
                }
              }
            }
          }
        }
        items(entries, key = { it.id }) { entry ->
          ActivityRow(entry, strings) {
            entry.postId?.let { onOpen(it, entry.threadCommentId) }
          }
          if (entry.id == entries.last().id && !reachedEnd && !loadingMore) {
            LaunchedEffect(entry.id) {
              loadingMore = true
              val page = runCatching { viewModel.inbox.activity(PAGE_SIZE, entries.size) }.getOrNull()
              if (page != null) {
                val known = entries.map { it.id }.toSet()
                activity = entries + page.filterNot { it.id in known }
                reachedEnd = page.size < PAGE_SIZE
              }
              loadingMore = false
            }
          }
        }
      }
    }
  }
}

/**
 * One inbox entry: the newest actors' avatars, « **Marie** and 3 others liked
 * your post », the content's words, when. A new entry is tinted and dotted.
 */
@Composable
private fun ActivityRow(entry: CommunityActivity, strings: CommunityStrings, onClick: () -> Unit) {
  val accent = MaterialTheme.colorScheme.primary
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(if (entry.isRead) Color.Transparent else accent.copy(alpha = 0.06f))
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    ActivityAvatars(entry)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(sentence(entry, strings), fontSize = 14.sp, lineHeight = 18.sp, color = CommunityColors.textPrimary)
      entry.excerpt?.trim()?.takeIf { it.isNotEmpty() }?.let {
        Text(
          it,
          fontSize = 14.sp,
          lineHeight = 18.sp,
          color = CommunityColors.textSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(relativeTime(entry.createdAtMillis, strings), fontSize = 12.sp, color = CommunityColors.textTertiary)
    }
    if (!entry.isRead) {
      Box(Modifier.padding(top = 6.dp).size(8.dp).background(accent, CircleShape))
    }
  }
}

/** The newest actor in front; a second one peeks behind for a group. */
@Composable
private fun ActivityAvatars(entry: CommunityActivity) {
  val first = entry.actors.firstOrNull()
  val second = entry.actors.getOrNull(1)?.takeIf { entry.actorCount > 1 }
  Box(modifier = Modifier.size(40.dp)) {
    CommunityAvatar(first?.avatarUrl, first?.nickname.orEmpty(), size = 40)
    if (second != null) {
      CommunityAvatar(
        second.avatarUrl,
        second.nickname,
        size = 22,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .offset(x = 6.dp, y = 4.dp)
          .border(2.dp, CommunityColors.background, CircleShape),
      )
    }
  }
}

/** The sentence, the first actor's name in bold. */
private fun sentence(entry: CommunityActivity, strings: CommunityStrings): AnnotatedString {
  val name = entry.actors.firstOrNull()?.nickname ?: strings.activitySomeone
  val others = if (entry.actorCount > 1) strings.activityOthers(entry.actorCount - 1) else null
  val text = strings.activitySentence(entry.type, name, others)
  val start = text.indexOf(name)
  return buildAnnotatedString {
    append(text)
    if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + name.length)
  }
}
