package io.appwin.community.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityMediaType
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.isVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Writing a post (text, optional photos, group picker, poll) - mirrors iOS `ComposerView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerScreen(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  editingPost: CommunityPost? = null,
  onPublished: () -> Unit = {},
  onDone: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isEditing = editingPost != null
  var body by remember(editingPost?.id) { mutableStateOf(editingPost?.body.orEmpty()) }
  var sending by remember { mutableStateOf(false) }
  var uploading by remember { mutableStateOf(false) }
  var groupId by remember(editingPost?.id) {
    mutableStateOf(
      editingPost?.groupId
        ?: state.selectedGroupId
        ?: state.groups.firstOrNull { it.canPost }?.id,
    )
  }
  var media by remember(editingPost?.id) {
    mutableStateOf(editingPost?.media.orEmpty())
  }
  var showsPoll by remember { mutableStateOf(false) }
  var pollOptions by remember { mutableStateOf(listOf("", "")) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var editingProfile by remember { mutableStateOf(false) }
  var bannerDismissed by remember { mutableStateOf(false) }
  /** The classifier removed the post as it was written: say so rather than show it. */
  var removedNotice by remember { mutableStateOf(false) }

  val limit = state.config.limits.postMaxLength
  val maxImages = state.config.limits.maxImagesPerPost
  val remaining = limit - body.length
  val showsCounter = remaining <= limit / 5
  val postable = remember(state.groups, editingPost?.groupId) {
    val base = state.groups.filter { it.canPost }.toMutableList()
    val currentId = editingPost?.groupId
    if (currentId != null && base.none { it.id == currentId }) {
      state.groups.firstOrNull { it.id == currentId }?.let { base.add(0, it) }
    }
    base
  }
  val canSubmit =
    body.isNotBlank() && remaining >= 0 && !sending && !uploading
  val pillBrush = accentPillBrush(state.config)
  val sendBrush = accentComposeBrush(state.config)
  val sendShadow = accentShadowColor(state.config)

  val imagePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri == null || media.size >= maxImages) return@rememberLauncherForActivityResult
    uploading = true
    errorMessage = null
    scope.launch {
      runCatching {
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("empty")
        val (compressed, mime) = ImageCompressor.compress(bytes, ImageCompressor.PostMaxSidePx)
        viewModel.uploadMedia(compressed, mime, "post.jpg")
      }.onSuccess { uploaded ->
        media = media + CommunityMedia(
          url = uploaded.publicUrl,
          width = uploaded.width,
          height = uploaded.height,
          type = CommunityMediaType.IMAGE,
        )
      }.onFailure { errorMessage = it.message }
      uploading = false
    }
  }

  val videoPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri == null || media.size >= maxImages) return@rememberLauncherForActivityResult
    uploading = true
    errorMessage = null
    scope.launch {
      try {
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("empty")
        if (bytes.size > MAX_COMMUNITY_VIDEO_BYTES) {
          errorMessage = strings.videoTooLarge
          return@launch
        }
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val uploaded = viewModel.uploadMedia(bytes, mime, "post.mp4")
        media = media + CommunityMedia(
          url = uploaded.publicUrl,
          width = uploaded.width,
          height = uploaded.height,
          type = CommunityMediaType.VIDEO,
        )
      } catch (e: Exception) {
        errorMessage = e.message
      } finally {
        uploading = false
      }
    }
  }

  fun submit() {
    if (!canSubmit) return
    sending = true
    errorMessage = null
    val trimmed = body.trim()
    if (editingPost != null) {
      viewModel.updatePost(editingPost.id, trimmed, groupId, media) { ok ->
        sending = false
        if (ok) onDone() else errorMessage = strings.loadErrorMessage
      }
      return
    }
    val poll = if (showsPoll) {
      pollOptions.map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.size >= 2 }
    } else {
      null
    }
    viewModel.createPost(trimmed, groupId, media, poll) { outcome ->
      sending = false
      when (outcome) {
        PublishOutcome.PUBLISHED -> {
          onPublished()
          onDone()
        }
        PublishOutcome.REMOVED -> removedNotice = true
        PublishOutcome.FAILED -> errorMessage = strings.loadErrorMessage
      }
    }
  }

  // Keep composer draft mounted while editing profile (body / media / poll).
  if (editingProfile && state.profile != null) {
    BackHandler { editingProfile = false }
    EditProfileScreen(
      profile = state.profile!!,
      viewModel = viewModel,
      strings = strings,
      leaveAnonymity = true,
      onBack = { editingProfile = false },
      onSaved = { editingProfile = false },
    )
    return
  }

  Scaffold(
    containerColor = CommunityColors.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TextNavBar(
        title = if (isEditing) strings.editPost else strings.newPost,
        leadingLabel = strings.cancel,
        onLeading = onDone,
        containerColor = CommunityColors.background,
      )
    },
    bottomBar = {
      ComposerActionBar(
        strings = strings,
        imagesEnabled = state.config.features.imagesEnabled && state.allows { it.images },
        videosEnabled = state.allows { it.videos },
        canAddPhoto = media.size < maxImages && !uploading,
        showsPoll = showsPoll,
        allowPoll = !isEditing && state.allows { it.polls },
        showsCounter = showsCounter,
        remaining = remaining,
        canSubmit = canSubmit,
        sendLabel = if (isEditing) strings.save else strings.publish,
        sendBrush = sendBrush,
        sendShadow = sendShadow,
        onAddPhoto = {
          imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
          )
        },
        onAddVideo = {
          videoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
          )
        },
        onTogglePoll = {
          showsPoll = !showsPoll
          if (showsPoll && pollOptions.size < 2) pollOptions = listOf("", "")
        },
        onSend = ::submit,
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      if (state.profile?.isAnonymous == true && !bannerDismissed) {
        AnonymousBanner(
          nickname = state.profile?.nickname.orEmpty(),
          strings = strings,
          onEditProfile = { openProfileEditor { editingProfile = true } },
          onDismiss = { bannerDismissed = true },
        )
      }

      if (postable.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(postable, key = { it.id }) { group ->
            val selected = group.id == groupId
            Text(
              text = listOfNotNull(group.emoji, group.name).joinToString(" "),
              fontSize = 13.sp,
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
              color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
              } else {
                CommunityColors.textSecondary
              },
              modifier = Modifier
                .clip(CircleShape)
                .then(
                  if (selected) Modifier.background(pillBrush)
                  else Modifier.background(CommunityColors.surface),
                )
                .clickable { groupId = group.id }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            )
          }
        }
      }

      // Borderless editor like iOS TextEditor - not Material OutlinedTextField.
      // Explicit lineHeight (not bodyLarge's 24sp): Material's default makes the
      // caret look oversized next to 16sp glyphs. Shared padding keeps the
      // caret on the same baseline as the placeholder.
      BasicTextField(
        value = body,
        onValueChange = { if (it.length <= limit) body = it },
        textStyle = TextStyle(
          fontSize = 16.sp,
          lineHeight = 22.sp,
          fontWeight = FontWeight.Normal,
          color = CommunityColors.textPrimary,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 160.dp)
          .padding(top = 8.dp, start = 2.dp),
        decorationBox = { inner ->
          Box(modifier = Modifier.fillMaxWidth()) {
            if (body.isEmpty()) {
              Text(
                text = strings.composerPlaceholder,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = CommunityColors.textTertiary,
              )
            }
            inner()
          }
        },
      )

      // Attachments under the draft - same order as the published post card.
      if (media.isNotEmpty() || uploading) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(media, key = { it.url }) { item ->
            Box {
              if (item.isVideo) {
                CommunityVideoThumb(
                  url = item.url,
                  modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                )
              } else {
                AsyncImage(
                  model = item.url,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                )
              }
              // Inside the thumbnail: offset past its edge, the LazyRow clipped it.
              Box(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(4.dp)
                  .size(20.dp)
                  .clip(CircleShape)
                  .background(Color.Black.copy(alpha = 0.55f))
                  .clickable { media = media.filterNot { it.url == item.url } },
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = strings.cancel,
                  tint = Color.White,
                  modifier = Modifier.size(12.dp),
                )
              }
            }
          }
          if (uploading) {
            item {
              Box(
                modifier = Modifier
                  .size(72.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(CommunityColors.border.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(26.dp),
                  color = MaterialTheme.colorScheme.primary,
                  strokeWidth = 2.5.dp,
                )
              }
            }
          }
        }
      }

      if (showsPoll) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          pollOptions.forEachIndexed { index, value ->
            BasicTextField(
              value = value,
              onValueChange = { next ->
                pollOptions = pollOptions.toMutableList().also { it[index] = next }
              },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = CommunityColors.textPrimary,
              ),
              keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CommunityColors.surface)
                .padding(10.dp),
              decorationBox = { inner ->
                Box {
                  if (value.isEmpty()) {
                    Text(
                      text = "${strings.pollOption} ${index + 1}",
                      color = CommunityColors.textTertiary,
                      style = MaterialTheme.typography.bodyMedium,
                    )
                  }
                  inner()
                }
              },
            )
          }
          if (pollOptions.size < 6) {
            Text(
              text = strings.addPollOption,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.clickable { pollOptions = pollOptions + "" },
            )
          }
        }
      }

      errorMessage?.let { message ->
        Text(
          text = message,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }

  if (removedNotice) {
    RemovedByModerationAlert(strings.postRemovedAlertTitle, strings) {
      removedNotice = false
      onDone()
    }
  }
}

/** Matches API `community_media` purpose max size for video/mp4 + video/quicktime. */
private const val MAX_COMMUNITY_VIDEO_BYTES = 75 * 1024 * 1024

/** What became of a post sent from the composer. */
internal enum class PublishOutcome { PUBLISHED, REMOVED, FAILED }
