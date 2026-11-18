package io.appwin.community.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.domain.ModerationQueueItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A moderator answers the author of a queued content. The answer is a plain
 * comment under the post: a hidden post stays hidden from the community, but
 * its author still sees it, and the comment with it.
 */
@Composable
internal fun ModerationReplySheet(
  item: ModerationQueueItem,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onDismiss: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var text by remember { mutableStateOf("") }
  var sending by remember { mutableStateOf(false) }
  var sent by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  val trimmed = text.trim()
  val authorName = (item.comment?.author ?: item.post.author)?.nickname.orEmpty()

  fun send() {
    if (trimmed.isEmpty()) return
    sending = true
    failed = false
    // Under the comment when the community has replies; otherwise under the post.
    val parentId = if (state.config.features.repliesEnabled) item.comment?.id else null
    scope.launch {
      viewModel.createComment(item.post.id, trimmed, parentId)
        .onSuccess {
          sent = true
          delay(1_200)
          onDismiss()
        }
        .onFailure { failed = true }
      sending = false
    }
  }

  CommunitySheet(
    title = strings.moderationReplyTitle,
    leadingLabel = strings.cancel,
    onDismiss = onDismiss,
    trailing = {
      if (!sent) {
        CommunityActionPill(
          title = strings.send,
          enabled = trimmed.isNotEmpty(),
          loading = sending,
          onClick = ::send,
        )
      }
    },
  ) {
    if (sent) {
      Text(strings.moderationReplySent, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textPrimary)
    } else {
      CommunityInfoBox(strings.moderationReplyInfo(authorName))
      CommunityTextArea(
        label = strings.message,
        value = text,
        onValueChange = { text = it },
        placeholder = strings.moderationReplyPlaceholder,
        strings = strings,
        limit = state.config.limits.commentMaxLength,
      )
      if (failed) CommunityErrorLine(strings)
    }
  }
}
