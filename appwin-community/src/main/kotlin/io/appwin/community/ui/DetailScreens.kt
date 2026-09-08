package io.appwin.community.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.domain.CommunityReportReason
import kotlinx.coroutines.launch

/**
 * Post detail: the post at the top, its comments below, the reply field at the
 * bottom.
 *
 * Comments load on open rather than being prefetched with the feed: a page of
 * twenty posts would pull hundreds, most of which are never displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostDetailScreen(
  postId: String,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onBack: () -> Unit,
  onOpenProfile: (String) -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val post = state.posts.firstOrNull { it.id == postId }
  val scope = rememberCoroutineScope()

  var comments by remember(postId) { mutableStateOf<List<CommunityComment>>(emptyList()) }
  var loading by remember(postId) { mutableStateOf(true) }
  var draft by remember(postId) { mutableStateOf("") }
  var sending by remember(postId) { mutableStateOf(false) }

  LaunchedEffect(postId) {
    loading = true
    comments = viewModel.comments(postId).getOrDefault(emptyList())
    loading = false
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.comments) },
        navigationIcon = { BackButton(onBack) },
      )
    },
    bottomBar = {
      if (state.config.features.commentsEnabled && state.profile?.isBanned != true) {
        CommentComposer(
          value = draft,
          placeholder = strings.commentPlaceholder,
          maxLength = state.config.limits.commentMaxLength,
          sending = sending,
          onChange = { draft = it },
          onSend = {
            val body = draft.trim()
            if (body.isEmpty() || sending) return@CommentComposer
            sending = true
            scope.launch {
              viewModel.createComment(postId, body, null)
                .onSuccess { comments = comments + it; draft = "" }
              sending = false
            }
          },
        )
      }
    },
  ) { padding ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
      post?.let {
        item {
          PostCard(
            post = it,
            config = state.config,
            strings = strings,
            onOpen = {},
            onReact = { kind -> viewModel.toggleReaction(it, kind) },
            onOpenProfile = onOpenProfile,
            onDelete = { viewModel.deletePost(it.id); onBack() },
            onReport = {},
          )
        }
      }

      item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }

      if (loading) {
        item {
          Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
      } else if (comments.isEmpty()) {
        item {
          Text(
            text = strings.noComments,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
          )
        }
      } else {
        items(comments, key = { it.id }) { comment ->
          CommentRow(comment, strings, onOpenProfile, indent = 0)
          // One level of reply, as on iOS: beyond that a thread becomes
          // unreadable on a phone screen.
          comment.replies.forEach { CommentRow(it, strings, onOpenProfile, indent = 1) }
        }
      }
    }
  }
}

@Composable
private fun CommentRow(
  comment: CommunityComment,
  strings: CommunityStrings,
  onOpenProfile: (String) -> Unit,
  indent: Int,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = (16 + indent * 28).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    AuthorRow(
      author = comment.author,
      subtitle = relativeTime(comment.createdAtMillis),
      strings = strings,
      onClick = comment.author?.id?.let { id -> { onOpenProfile(id) } },
    )
    if (comment.isPendingReview) NoticeBanner(strings.pendingReview)
    Text(
      text = comment.translatedBody ?: comment.body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun CommentComposer(
  value: String,
  placeholder: String,
  maxLength: Int,
  sending: Boolean,
  onChange: (String) -> Unit,
  onSend: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      value = value,
      // The limit applies while typing, not on send: being refused text you
      // have just written is the worst moment to learn it is too long.
      onValueChange = { if (it.length <= maxLength) onChange(it) },
      placeholder = { Text(placeholder) },
      modifier = Modifier.weight(1f),
      maxLines = 4,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
    )
    IconButton(onClick = onSend, enabled = value.isNotBlank() && !sending) {
      if (sending) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
      } else {
        Icon(Icons.Default.Send, contentDescription = null)
      }
    }
  }
}

/** Writing a post. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerScreen(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onDone: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var body by remember { mutableStateOf("") }
  var sending by remember { mutableStateOf(false) }
  val max = state.config.limits.postMaxLength

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.newPost) },
        navigationIcon = { BackButton(onDone) },
        actions = {
          TextButton(
            onClick = {
              if (body.isBlank() || sending) return@TextButton
              sending = true
              viewModel.createPost(body.trim()) { ok ->
                sending = false
                if (ok) onDone() else viewModel.emit(strings.loadErrorMessage)
              }
            },
            enabled = body.isNotBlank() && !sending,
          ) { Text(strings.publish) }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = body,
        onValueChange = { if (it.length <= max) body = it },
        placeholder = { Text(strings.composerPlaceholder) },
        modifier = Modifier.fillMaxWidth().weight(1f),
      )
      Text(
        text = "${body.length} / $max",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.End),
      )
    }
  }
}

/** A member's profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
  profileId: String,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onBack: () -> Unit,
) {
  var profile by remember(profileId) { mutableStateOf<CommunityProfile?>(null) }
  var failed by remember(profileId) { mutableStateOf(false) }

  LaunchedEffect(profileId) {
    viewModel.profile(profileId)
      .onSuccess { profile = it }
      .onFailure { failed = true }
  }

  Scaffold(
    topBar = {
      TopAppBar(title = { Text(strings.profile) }, navigationIcon = { BackButton(onBack) })
    },
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      when {
        failed -> CommunityEmptyState(
          icon = Icons.Default.Info,
          title = strings.loadErrorTitle,
          message = strings.loadErrorMessage,
        )

        profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }

        else -> profile?.let { member ->
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CommunityAvatar(member.avatarUrl, member.nickname, size = 88)
            Text(member.nickname, style = MaterialTheme.typography.titleLarge)
            member.bio?.takeIf { it.isNotBlank() }?.let {
              Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
              Stat(member.postCount.toString(), strings.posts)
              Stat(member.commentCount.toString(), strings.comments)
              Stat(member.receivedReactionCount.toString(), strings.reactionsReceived)
            }
            if (member.isBanned) NoticeBanner(strings.bannedNotice)
          }
        }
      }
    }
  }
}

@Composable
private fun Stat(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleMedium)
    Text(
      label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Report sheet: pick a reason, then send. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSheet(
  strings: CommunityStrings,
  onDismiss: () -> Unit,
  onConfirm: (CommunityReportReason) -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Text(
      text = strings.reportTitle,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    CommunityReportReason.entries.forEach { reason ->
      ListItem(
        headlineContent = { Text(strings.reportReason(reason.wire)) },
        modifier = Modifier.fillMaxWidth().clickable { onConfirm(reason) },
      )
    }
    TextButton(
      onClick = onDismiss,
      modifier = Modifier.fillMaxWidth().padding(24.dp),
    ) { Text(strings.cancel) }
  }
}
