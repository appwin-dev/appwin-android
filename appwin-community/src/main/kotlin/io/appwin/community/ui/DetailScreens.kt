package io.appwin.community.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityPost
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
            onVote = { optionId -> viewModel.voteOnPoll(it, optionId) },
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
      subtitle = relativeTime(comment.createdAtMillis, strings),
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
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
      }
    }
  }
}

/** Writing a post (text, optional photos, group picker, poll). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerScreen(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onDone: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var body by remember { mutableStateOf("") }
  var sending by remember { mutableStateOf(false) }
  var uploading by remember { mutableStateOf(false) }
  var groupId by remember {
    mutableStateOf(state.selectedGroupId ?: state.groups.firstOrNull { it.canPost }?.id)
  }
  var media by remember { mutableStateOf<List<CommunityMedia>>(emptyList()) }
  var showsPoll by remember { mutableStateOf(false) }
  var pollOptions by remember { mutableStateOf(listOf("", "")) }
  var showVideoSoon by remember { mutableStateOf(false) }
  val max = state.config.limits.postMaxLength
  val maxImages = state.config.limits.maxImagesPerPost
  val postable = state.groups.filter { it.canPost }
  val canSubmit = body.isNotBlank() && !sending && !uploading

  val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri == null || media.size >= maxImages) return@rememberLauncherForActivityResult
    uploading = true
    scope.launch {
      runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("empty")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        viewModel.uploadMedia(bytes, mime, "post.jpg")
      }.onSuccess { uploaded ->
        media = media + CommunityMedia(uploaded.publicUrl, uploaded.width, uploaded.height, null)
      }
      uploading = false
    }
  }

  fun submit() {
    if (!canSubmit) return
    sending = true
    val poll = if (showsPoll) {
      pollOptions.map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.size >= 2 }
    } else {
      null
    }
    viewModel.createPost(body.trim(), groupId, media, poll) { ok ->
      sending = false
      if (ok) onDone() else viewModel.emit(strings.loadErrorMessage)
    }
  }

  if (showVideoSoon) {
    AlertDialog(
      onDismissRequest = { showVideoSoon = false },
      title = { Text(strings.addVideo) },
      text = { Text(strings.videoComingSoon) },
      confirmButton = {
        TextButton(onClick = { showVideoSoon = false }) { Text(strings.cancel) }
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.newPost) },
        navigationIcon = { BackButton(onDone) },
      )
    },
    bottomBar = {
      Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider()
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (state.config.features.imagesEnabled) {
            ComposerActionIcon(
              icon = Icons.Default.Photo,
              label = strings.addPhoto,
              enabled = media.size < maxImages && !uploading,
              onClick = {
                picker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
              },
            )
          }
          ComposerActionIcon(
            icon = Icons.Default.Videocam,
            label = strings.addVideo,
            onClick = { showVideoSoon = true },
          )
          ComposerActionIcon(
            icon = Icons.Default.BarChart,
            label = strings.addPoll,
            tint = if (showsPoll) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = {
              showsPoll = !showsPoll
              if (showsPoll && pollOptions.size < 2) pollOptions = listOf("", "")
            },
          )
          Spacer(modifier = Modifier.weight(1f))
          Button(
            onClick = ::submit,
            enabled = canSubmit,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
              disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
            ),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.Send,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(strings.send)
          }
        }
      }
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (postable.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(postable, key = { it.id }) { group ->
            val selected = group.id == groupId
            Text(
              text = listOfNotNull(group.emoji, group.name).joinToString(" "),
              style = MaterialTheme.typography.labelLarge,
              color = if (selected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceVariant,
                )
                .clickable { groupId = group.id }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      if (media.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(media, key = { it.url }) { item ->
            Box {
              AsyncImage(
                model = item.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(72.dp)
                  .clip(MaterialTheme.shapes.small),
              )
              Text(
                text = "×",
                color = Color.White,
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .clickable { media = media.filterNot { it.url == item.url } }
                  .padding(4.dp),
              )
            }
          }
          if (uploading) {
            item {
              Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
              }
            }
          }
        }
      }

      if (showsPoll) {
        pollOptions.forEachIndexed { index, value ->
          OutlinedTextField(
            value = value,
            onValueChange = { next ->
              pollOptions = pollOptions.toMutableList().also { it[index] = next }
            },
            label = { Text("${strings.pollOption} ${index + 1}") },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (pollOptions.size < 6) {
          TextButton(onClick = { pollOptions = pollOptions + "" }) {
            Text(strings.addPollOption)
          }
        }
      }

      OutlinedTextField(
        value = body,
        onValueChange = { if (it.length <= max) body = it },
        placeholder = { Text(strings.composerPlaceholder) },
        modifier = Modifier.fillMaxWidth().weight(1f),
      )
    }
  }
}

@Composable
private fun ComposerActionIcon(
  icon: ImageVector,
  label: String,
  enabled: Boolean = true,
  tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick, enabled = enabled) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (enabled) tint else tint.copy(alpha = 0.4f),
      modifier = Modifier.size(20.dp),
    )
  }
}

/** A member's profile with publications and optional edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
  profileId: String,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onBack: () -> Unit,
  onCompose: () -> Unit = {},
  onOpenPost: (CommunityPost) -> Unit = {},
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var profile by remember(profileId) { mutableStateOf<CommunityProfile?>(null) }
  var failed by remember(profileId) { mutableStateOf(false) }
  var editing by remember { mutableStateOf(false) }
  var posts by remember(profileId) { mutableStateOf<List<CommunityPost>>(emptyList()) }
  var postsLoading by remember(profileId) { mutableStateOf(true) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(profileId) {
    viewModel.profile(profileId)
      .onSuccess { profile = it }
      .onFailure { failed = true }
    postsLoading = true
    runCatching { viewModel.authorPosts(profileId) }
      .onSuccess { posts = it.items }
      .onFailure { }
    postsLoading = false
  }

  if (editing && profile?.isMe == true) {
    EditProfileScreen(
      profile = profile!!,
      viewModel = viewModel,
      strings = strings,
      onBack = { editing = false },
      onSaved = { updated ->
        profile = updated
        editing = false
      },
    )
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.profile) },
        navigationIcon = { BackButton(onBack) },
      )
    },
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      when {
        failed -> CommunityEmptyState(
          icon = Icons.Default.Info,
          title = strings.loadErrorTitle,
          message = strings.loadErrorMessage,
        )

        profile == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }

        else -> profile?.let { member ->
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            item {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Box(
                  modifier = Modifier.clickable(enabled = member.isMe) { editing = true },
                ) {
                  CommunityAvatar(member.avatarUrl, member.nickname, size = 72)
                  if (member.isMe) {
                    Box(
                      modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(
                        "+",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                      )
                    }
                  }
                }
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(member.nickname, style = MaterialTheme.typography.titleLarge)
                    if (member.isTeam) TeamBadge(strings.teamBadge)
                  }
                  Text(
                    text = "${member.postCount} ${strings.posts}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            member.bio?.takeIf { it.isNotBlank() }?.let { bioText ->
              item {
                Text(
                  bioText,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            if (member.isMe) {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  ProfileActionChip(
                    label = "+${strings.addBio}",
                    modifier = Modifier.weight(1f),
                    onClick = { editing = true },
                  )
                  ProfileActionChip(
                    label = strings.editProfile,
                    modifier = Modifier.weight(1f),
                    onClick = { editing = true },
                  )
                }
              }
            }

            if (member.isBanned) {
              item { NoticeBanner(strings.bannedNotice) }
            }

            item {
              Text(
                text = strings.publications,
                style = MaterialTheme.typography.titleMedium,
              )
            }

            when {
              postsLoading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator()
                }
              }
              posts.isEmpty() -> item {
                CommunityEmptyState(
                  icon = Icons.Default.Info,
                  title = if (member.isMe) strings.noPublicationsYet else strings.noPublicationsOther,
                  message = "",
                  actionLabel = if (member.isMe) strings.createFirstPost else null,
                  onAction = if (member.isMe) onCompose else null,
                )
              }
              else -> items(posts, key = { it.id }) { post ->
                PostCard(
                  post = post,
                  config = state.config,
                  strings = strings,
                  onOpen = { onOpenPost(post) },
                  onReact = { kind -> viewModel.toggleReaction(post, kind) },
                  onOpenProfile = {},
                  onDelete = { viewModel.deletePost(post.id) },
                  onReport = {},
                  onVote = { optionId -> viewModel.voteOnPoll(post, optionId) },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ProfileActionChip(
  label: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Text(
    text = label,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurface,
    maxLines = 1,
    textAlign = TextAlign.Center,
    modifier = modifier
      .clip(RoundedCornerShape(999.dp))
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileScreen(
  profile: CommunityProfile,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onBack: () -> Unit,
  onSaved: (CommunityProfile) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var nickname by remember { mutableStateOf(if (profile.isAnonymous) "" else profile.nickname) }
  var bio by remember { mutableStateOf(profile.bio.orEmpty()) }
  var isAnonymous by remember { mutableStateOf(profile.isAnonymous) }
  var avatarUrl by remember { mutableStateOf(profile.avatarUrl) }
  var saving by remember { mutableStateOf(false) }
  var uploading by remember { mutableStateOf(false) }
  val bioLimit = 200
  val canSave = !saving && !uploading && (isAnonymous || nickname.trim().length >= 2)

  val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    uploading = true
    scope.launch {
      runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("empty")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        viewModel.uploadMedia(bytes, mime, "avatar.jpg")
      }.onSuccess { avatarUrl = it.publicUrl }
      uploading = false
    }
  }

  fun save() {
    if (!canSave) return
    saving = true
    scope.launch {
      runCatching {
        viewModel.updateOwnProfile(
          nickname = if (isAnonymous) null else nickname.trim(),
          bio = bio.trim(),
          avatarUrl = avatarUrl,
          isAnonymous = isAnonymous,
        )
      }.onSuccess(onSaved)
      saving = false
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.editProfileTitle) },
        navigationIcon = { BackButton(onBack) },
        actions = {
          Button(
            onClick = ::save,
            enabled = canSave,
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          ) {
            Text(strings.save)
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 20.dp, vertical = 16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier.clickable {
          picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
          )
        },
      ) {
        CommunityAvatar(avatarUrl, nickname.ifBlank { profile.nickname }, size = 112)
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Default.Edit,
            contentDescription = strings.addPhoto,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp),
          )
        }
        if (uploading) {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
      }

      if (!isAnonymous) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(strings.nickname, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
          )
          Text(
            strings.nicknameHelp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(strings.bio, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
          value = bio,
          onValueChange = { if (it.length <= bioLimit) bio = it },
          modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
          shape = RoundedCornerShape(14.dp),
          placeholder = { Text(strings.bioPlaceholder) },
          minLines = 5,
        )
        Text(
          text = "${bio.length}/$bioLimit",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.End,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(strings.anonymous)
        androidx.compose.material3.Switch(
          checked = isAnonymous,
          onCheckedChange = { isAnonymous = it },
        )
      }
    }
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
