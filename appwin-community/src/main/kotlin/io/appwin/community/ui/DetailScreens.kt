package io.appwin.community.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityFeatures
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityMediaType
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.CommunityReportReason
import io.appwin.community.domain.isVideo
import io.appwin.community.domain.optimisticReactionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  onEditPost: (CommunityPost) -> Unit = {},
  initialThreadCommentId: String? = null,
  fromPushDeeplink: Boolean = false,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val post = state.post(postId)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  var comments by remember(postId) { mutableStateOf<List<CommunityComment>>(emptyList()) }
  var loading by remember(postId) { mutableStateOf(true) }
  var postMissing by remember(postId) { mutableStateOf(false) }
  var draft by remember(postId) { mutableStateOf("") }
  var sending by remember(postId) { mutableStateOf(false) }
  var uploading by remember(postId) { mutableStateOf(false) }
  var replyTo by remember(postId) { mutableStateOf<CommunityComment?>(null) }
  var pendingMedia by remember(postId) { mutableStateOf<List<CommunityMedia>>(emptyList()) }
  var reportTarget by remember { mutableStateOf<String?>(null) }
  // When set, the dedicated "Réponses" screen is shown for that root comment.
  var threadRootId by remember(postId) { mutableStateOf(initialThreadCommentId) }
  val commentsListState = rememberLazyListState()
  // Bumped after a successful send on the replies screen so it scrolls to the new row.
  var replyThreadScrollTick by remember(postId) { mutableIntStateOf(0) }

  val features = state.config.features
  val maxImages = state.config.limits.maxImagesPerPost
  val canSend =
    (draft.isNotBlank() || pendingMedia.isNotEmpty()) && !sending && !uploading
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val dismissKeyboard: () -> Unit = {
    focusManager.clearFocus(force = true)
    keyboardController?.hide()
  }

  /** Same two-pass settle as iOS after keyboard / list layout catch up. */
  suspend fun scrollCommentsToEnd(state: LazyListState) {
    delay(100)
    val last = state.layoutInfo.totalItemsCount - 1
    if (last >= 0) state.animateScrollToItem(last)
    delay(150)
    val lastAgain = state.layoutInfo.totalItemsCount - 1
    if (lastAgain >= 0) state.animateScrollToItem(lastAgain)
  }

  val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri == null || pendingMedia.size >= maxImages) return@rememberLauncherForActivityResult
    uploading = true
    scope.launch {
      runCatching {
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("empty")
        val (compressed, mime) = ImageCompressor.compress(bytes, ImageCompressor.PostMaxSidePx)
        viewModel.uploadMedia(compressed, mime, "comment.jpg")
      }.onSuccess { uploaded ->
        pendingMedia = pendingMedia + CommunityMedia(
          uploaded.publicUrl,
          uploaded.width,
          uploaded.height,
          null,
        )
      }
      uploading = false
    }
  }

  LaunchedEffect(postId) {
    loading = true
    postMissing = !viewModel.loadPost(postId)
    comments = viewModel.comments(postId).getOrDefault(emptyList())
    loading = false
    // Detail open counts as a view even if the feed never flushed.
    viewModel.trackVisible(listOf(postId))
  }

  fun insertComment(created: CommunityComment) {
    val parentId = created.parentCommentId
    if (parentId == null) {
      comments = comments + created
      return
    }
    var rootIndex = comments.indexOfFirst { it.id == parentId }
    if (rootIndex < 0) {
      // Parent may still be a nested reply id: attach under that reply's root.
      rootIndex = comments.indexOfFirst { root -> root.replies.any { it.id == parentId } }
    }
    if (rootIndex < 0) {
      comments = comments + created
      return
    }
    val parent = comments[rootIndex]
    comments = comments.toMutableList().also {
      it[rootIndex] = parent.copy(
        replies = parent.replies + created,
        replyCount = parent.replyCount + 1,
      )
    }
  }

  fun patchComment(commentId: String, transform: (CommunityComment) -> CommunityComment) {
    comments = comments.map { root ->
      if (root.id == commentId) transform(root)
      else root.copy(replies = root.replies.map { if (it.id == commentId) transform(it) else it })
    }
  }

  fun toggleCommentReaction(target: CommunityComment, kind: CommunityReactionKind) {
    val optimistic = optimisticReactionState(
      myReaction = target.myReaction,
      reactionCounts = target.reactionCounts,
      likeCount = target.likeCount,
      kind = kind,
    )
    val optimisticComment = target.copy(
      myReaction = optimistic.myReaction,
      topReactions = optimistic.topReactions,
      reactionCounts = optimistic.reactionCounts,
      likeCount = optimistic.likeCount,
    )
    patchComment(target.id) { optimisticComment }
    scope.launch {
      viewModel.reactToComment(postId, target.id, kind)
        .onSuccess { result ->
          patchComment(target.id) {
            it.copy(
              myReaction = result.myReaction,
              topReactions = result.topReactions,
              reactionCounts = result.reactionCounts,
              likeCount = result.likeCount,
            )
          }
        }
        .onFailure {
          patchComment(target.id) {
            it.copy(
              myReaction = target.myReaction,
              topReactions = target.topReactions,
              reactionCounts = target.reactionCounts,
              likeCount = target.likeCount,
            )
          }
        }
    }
  }

  fun openReplyThread(rootId: String, target: CommunityComment? = null) {
    replyTo = target ?: comments.firstOrNull { it.id == rootId }
    threadRootId = rootId
  }

  fun beginReply(target: CommunityComment) {
    val rootId = target.parentCommentId ?: target.id
    val root = comments.firstOrNull { it.id == rootId }
    replyTo = target
    // Long threads open the dedicated replies screen so the member keeps
    // context instead of typing against a collapsed list.
    if (root != null && root.replyCount > 1) {
      threadRootId = rootId
    }
  }

  val threadRoot = threadRootId?.let { id -> comments.firstOrNull { it.id == id } }
  if (threadRoot != null) {
    BackHandler { threadRootId = null }
    LaunchedEffect(threadRoot.id) {
      // Push deeplink: open the thread to read, do not pre-select reply target.
      if (!fromPushDeeplink && replyTo == null) replyTo = threadRoot
    }
    ReplyThreadScreen(
      root = threadRoot,
      strings = strings,
      features = features,
      commentMaxLength = state.config.limits.commentMaxLength,
      draft = draft,
      sending = sending,
      uploading = uploading,
      canSend = canSend,
      canCompose = features.commentsEnabled && state.profile?.isBanned != true,
      replyTo = replyTo,
      pendingMedia = pendingMedia,
      maxImages = maxImages,
      scrollToBottomTick = replyThreadScrollTick,
      onBack = { threadRootId = null },
      onDraftChange = { draft = it },
      onClearReply = { replyTo = threadRoot },
      onRemoveMedia = { index ->
        pendingMedia = pendingMedia.filterIndexed { i, _ -> i != index }
      },
      onAddPhoto = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
      onOpenProfile = onOpenProfile,
      onReact = { target, kind -> toggleCommentReaction(target, kind) },
      onReply = { target ->
        replyTo = target
      },
      onSend = {
        if (!canSend) return@ReplyThreadScreen
        val body = draft.trim()
        val media = pendingMedia
        val parentId = (replyTo ?: threadRoot).id
        sending = true
        dismissKeyboard()
        scope.launch {
          viewModel.createComment(postId, body, parentId, media)
            .onSuccess {
              insertComment(it)
              draft = ""
              pendingMedia = emptyList()
              replyTo = comments.firstOrNull { c -> c.id == threadRoot.id } ?: threadRoot
              replyThreadScrollTick += 1
            }
          sending = false
        }
      },
    )
    return
  }

  Scaffold(
    topBar = { TextNavBar(title = strings.commentTitle, leadingLabel = strings.back, onLeading = onBack) },
    containerColor = CommunityColors.background,
    bottomBar = {
      if (features.commentsEnabled && state.profile?.isBanned != true) {
        CommentComposer(
          avatarUrl = state.profile?.avatarUrl,
          avatarName = state.profile?.nickname.orEmpty(),
          value = draft,
          placeholder = strings.commentPlaceholder,
          maxLength = state.config.limits.commentMaxLength,
          sending = sending,
          uploading = uploading,
          canSend = canSend,
          replyToLabel = replyTo?.let { strings.replyingTo(it.author?.nickname.orEmpty()) },
          pendingMedia = pendingMedia,
          imagesEnabled = features.imagesEnabled,
          canAddPhoto = pendingMedia.size < maxImages && !uploading,
          photoLabel = strings.addPhoto,
          onChange = { draft = it },
          onClearReply = { replyTo = null },
          onRemoveMedia = { index ->
            pendingMedia = pendingMedia.filterIndexed { i, _ -> i != index }
          },
          onAddPhoto = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          onSend = {
            if (!canSend) return@CommentComposer
            val body = draft.trim()
            val media = pendingMedia
            val parentId = replyTo?.id
            val threadRootIdForNav = replyTo?.let { it.parentCommentId ?: it.id }
            sending = true
            dismissKeyboard()
            scope.launch {
              viewModel.createComment(postId, body, parentId, media)
                .onSuccess {
                  insertComment(it)
                  draft = ""
                  pendingMedia = emptyList()
                  replyTo = null
                  // After a reply on a thread with 2+ replies, open "Réponses"
                  // so the new message is visible (inline list stays collapsed).
                  if (threadRootIdForNav != null) {
                    val root = comments.firstOrNull { c -> c.id == threadRootIdForNav }
                    if (root != null && root.replyCount > 1) {
                      threadRootId = threadRootIdForNav
                      replyThreadScrollTick += 1
                    } else {
                      scrollCommentsToEnd(commentsListState)
                    }
                  } else {
                    scrollCommentsToEnd(commentsListState)
                  }
                }
              sending = false
            }
          },
        )
      }
    },
  ) { padding ->
    if (postMissing) {
      PostNotFound(strings, Modifier.padding(padding)) {
        scope.launch {
          postMissing = !viewModel.loadPost(postId)
          if (!postMissing) comments = viewModel.comments(postId).getOrDefault(comments)
        }
      }
      return@Scaffold
    }
    LazyColumn(
      state = commentsListState,
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .pointerInput(Unit) {
          detectTapGestures(onTap = { dismissKeyboard() })
        },
    ) {
      post?.let {
        item {
          PostCard(
            post = it,
            config = state.config,
            strings = strings,
            flat = true,
            onOpen = {},
            onReact = { kind -> viewModel.toggleReaction(it, kind) },
            onOpenProfile = onOpenProfile,
            onEdit = { onEditPost(it) },
            onDelete = { viewModel.deletePost(it.id); onBack() },
            onReport = { reportTarget = it.id },
            onVote = { optionId -> viewModel.voteOnPoll(it, optionId) },
          )
        }
      }

      if (loading) {
        item {
          Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
      } else if (comments.isEmpty()) {
        item { CommentsEmptyState(strings) }
      } else {
        // Figma 19:5233: a "Commentaires" label, then bubbles 16 apart.
        item {
          Text(
            text = strings.comments,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = CommunityColors.textTertiary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
          )
        }
        items(comments, key = { it.id }) { comment ->
          // One reply stays inline; from the second on, "See N replies".
          val collapseReplies = comment.replyCount > 1
          val showRepliesInline = comment.replies.isNotEmpty() && !collapseReplies

          Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CommentRow(
              comment = comment,
              strings = strings,
              canReact = features.reactionsEnabled,
              canReply = features.repliesEnabled,
              availableReactions = features.reactions,
              onOpenProfile = onOpenProfile,
              onReact = { target, kind -> toggleCommentReaction(target, kind) },
              onReply = { beginReply(it) },
              indent = 0,
              canOpenProfile = features.profilesEnabled,
            )
            // One level of reply, as on iOS: the API re-parents reply-to-reply
            // under the root so the thread stays flat on a phone screen.
            if (showRepliesInline) {
              comment.replies.forEach { reply ->
                CommentRow(
                  comment = reply,
                  strings = strings,
                  canReact = features.reactionsEnabled,
                  canReply = features.repliesEnabled,
                  availableReactions = features.reactions,
                  onOpenProfile = onOpenProfile,
                  onReact = { target, kind -> toggleCommentReaction(target, kind) },
                  onReply = { beginReply(it) },
                  indent = 1,
                  canOpenProfile = features.profilesEnabled,
                )
              }
            }
            if (collapseReplies) {
              ShowRepliesLink(strings.showReplies(comment.replyCount)) { openReplyThread(comment.id) }
            }
          }
        }
      }
    }
  }

  reportTarget?.let { targetId ->
    ReportSheet(
      strings = strings,
      onDismiss = { reportTarget = null },
      onConfirm = { reason ->
        viewModel.report("post", targetId, reason) {
          reportTarget = null
          viewModel.emit(strings.reportSent)
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyThreadScreen(
  root: CommunityComment,
  strings: CommunityStrings,
  features: CommunityFeatures,
  commentMaxLength: Int,
  draft: String,
  sending: Boolean,
  uploading: Boolean,
  canSend: Boolean,
  canCompose: Boolean,
  replyTo: CommunityComment?,
  pendingMedia: List<CommunityMedia>,
  maxImages: Int,
  scrollToBottomTick: Int = 0,
  onBack: () -> Unit,
  onDraftChange: (String) -> Unit,
  onClearReply: () -> Unit,
  onRemoveMedia: (Int) -> Unit,
  onAddPhoto: () -> Unit,
  onOpenProfile: (String) -> Unit,
  onReact: (CommunityComment, CommunityReactionKind) -> Unit,
  onReply: (CommunityComment) -> Unit,
  onSend: () -> Unit,
) {
  val showReplyBanner = replyTo != null && replyTo.id != root.id
  val listState = rememberLazyListState()

  LaunchedEffect(scrollToBottomTick) {
    if (scrollToBottomTick == 0) return@LaunchedEffect
    delay(100)
    val last = listState.layoutInfo.totalItemsCount - 1
    if (last >= 0) listState.animateScrollToItem(last)
    delay(150)
    val lastAgain = listState.layoutInfo.totalItemsCount - 1
    if (lastAgain >= 0) listState.animateScrollToItem(lastAgain)
  }

  Scaffold(
    topBar = { TextNavBar(title = strings.replies, leadingLabel = strings.back, onLeading = onBack) },
    containerColor = CommunityColors.background,
    bottomBar = {
      if (canCompose) {
        CommentComposer(
          avatarUrl = null,
          avatarName = "",
          value = draft,
          placeholder = strings.replyToComment,
          maxLength = commentMaxLength,
          sending = sending,
          uploading = uploading,
          canSend = canSend,
          replyToLabel = if (showReplyBanner) {
            strings.replyingTo(replyTo?.author?.nickname.orEmpty())
          } else {
            null
          },
          pendingMedia = pendingMedia,
          imagesEnabled = features.imagesEnabled,
          canAddPhoto = pendingMedia.size < maxImages && !uploading,
          photoLabel = strings.addPhoto,
          onChange = onDraftChange,
          onClearReply = onClearReply,
          onRemoveMedia = onRemoveMedia,
          onAddPhoto = onAddPhoto,
          onSend = onSend,
        )
      }
    },
  ) { padding ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        CommentRow(
          comment = root,
          strings = strings,
          canReact = features.reactionsEnabled,
          canReply = features.repliesEnabled,
          availableReactions = features.reactions,
          onOpenProfile = onOpenProfile,
          onReact = onReact,
          onReply = onReply,
          indent = 0,
          canOpenProfile = features.profilesEnabled,
        )
      }
      items(root.replies, key = { it.id }) { reply ->
        CommentRow(
          comment = reply,
          strings = strings,
          canReact = features.reactionsEnabled,
          canReply = features.repliesEnabled,
          availableReactions = features.reactions,
          onOpenProfile = onOpenProfile,
          onReact = onReact,
          onReply = onReply,
          indent = 1,
          canOpenProfile = features.profilesEnabled,
        )
      }
    }
  }
}

/**
 * One comment - Figma 19:5233: 24dp avatar, then a bg/low bubble with the
 * author line (Admin chip, age), the text, the like count top-right, and
 * "Répondre".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
  comment: CommunityComment,
  strings: CommunityStrings,
  canReact: Boolean,
  canReply: Boolean,
  availableReactions: List<CommunityReactionKind>,
  onOpenProfile: (String) -> Unit,
  onReact: (CommunityComment, CommunityReactionKind) -> Unit,
  onReply: (CommunityComment) -> Unit,
  indent: Int,
  canOpenProfile: Boolean = true,
) {
  val body = comment.translatedBody ?: comment.body
  val reactions = availableReactions.ifEmpty { listOf(CommunityReactionKind.LIKE) }
  val defaultKind = reactions.first()
  val pickerReactions =
    if (reactions.size > 1) reactions else CommunityReactionKind.entries.toList()
  var reactionPickerOpen by remember(comment.id) { mutableStateOf(false) }
  var showBreakdown by remember(comment.id) { mutableStateOf(false) }
  val openProfile = if (canOpenProfile) {
    comment.author?.takeIf { it.isAddressable }?.id?.let { id -> { onOpenProfile(id) } }
  } else {
    null
  }
  val bubbleShape = RoundedCornerShape(LocalCommunityConfig.current.theme.radius.dp.dp * 2 / 3)

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = (indent * 32).dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    CommunityAvatar(
      comment.author?.avatarUrl,
      comment.author?.nickname.orEmpty(),
      size = 24,
      modifier = if (openProfile != null) Modifier.clickable(onClick = openProfile) else Modifier,
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .clip(bubbleShape)
        .background(CommunityColors.surfaceMuted)
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
              .padding(end = 48.dp)
              .then(if (openProfile != null) Modifier.clickable(onClick = openProfile) else Modifier),
          ) {
            Text(
              text = comment.author?.nickname.orEmpty(),
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = CommunityColors.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            if (comment.author?.isTeam == true) TeamBadge(strings.teamBadge, onMutedSurface = true)
            Text(
              text = relativeTime(comment.createdAtMillis, strings),
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium,
              color = CommunityColors.textTertiary,
              maxLines = 1,
            )
          }
          if (comment.isPendingReview) NoticeBanner(strings.pendingReview)
          if (body.isNotBlank()) {
            Text(
              text = body,
              fontSize = 14.sp,
              lineHeight = 18.sp,
              color = CommunityColors.textPrimary,
            )
          }
        }

        if (canReact) {
          val reacted = comment.myReaction != null
          val tint = if (reacted) CommunityColors.like else CommunityColors.textTertiary
          Row(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .combinedClickable(
                onClick = { onReact(comment, defaultKind) },
                onLongClick = {
                  if (comment.likeCount > 0 && reactions.size > 1) showBreakdown = true
                  else reactionPickerOpen = true
                },
              ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = comment.likeCount.toString(),
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = tint,
            )
            Icon(
              imageVector = if (reacted) SolarIcons.HeartBold else SolarIcons.Heart,
              contentDescription = strings.like,
              tint = tint,
              modifier = Modifier.size(16.dp),
            )
          }
        }

        if (reactionPickerOpen) {
          Popup(
            alignment = Alignment.TopEnd,
            onDismissRequest = { reactionPickerOpen = false },
            offset = with(LocalDensity.current) { IntOffset(0, (-48).dp.roundToPx()) },
          ) {
            ReactionPickerPill(
              reactions = pickerReactions,
              active = comment.myReaction,
              onSelect = { kind ->
                onReact(comment, kind)
                reactionPickerOpen = false
              },
            )
          }
        }
      }

      if (comment.media.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(comment.media, key = { it.url }) { item ->
            val cell = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))
            if (item.isVideo) {
              CommunityVideoCell(
                url = item.url,
                contentDescription = item.alt,
                modifier = cell,
                closeLabel = strings.close,
              )
            } else {
              CommunityTappableImage(
                url = item.url,
                contentDescription = item.alt,
                closeLabel = strings.close,
                modifier = cell,
                contentScale = ContentScale.Crop,
              )
            }
          }
        }
      }

      // Reply is allowed on replies too: the API re-parents under the root.
      if (canReply) {
        Text(
          text = strings.reply,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = CommunityColors.textTertiary,
          modifier = Modifier.clickable { onReply(comment) },
        )
      }
    }
  }

  if (showBreakdown) {
    ReactionBreakdownSheet(
      counts = comment.reactionCounts,
      total = comment.likeCount,
      strings = strings,
      onDismiss = { showBreakdown = false },
    )
  }
}

/** Figma "Afficher 2 réponses": a short rule, the label, a chevron. */
@Composable
private fun ShowRepliesLink(label: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.padding(start = 32.dp).clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Box(modifier = Modifier.size(width = 20.dp, height = 1.dp).background(CommunityColors.border))
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = CommunityColors.textTertiary,
    )
    Icon(
      Icons.Default.KeyboardArrowDown,
      contentDescription = null,
      tint = CommunityColors.textTertiary,
      modifier = Modifier.size(12.dp),
    )
  }
}

/** Figma 29:5471: chat glyph, "Aucun commentaire pour l'instant", "Donne ton avis !". */
@Composable
private fun CommentsEmptyState(strings: CommunityStrings) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 80.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      SolarIcons.ChatLineDuotone,
      contentDescription = null,
      tint = CommunityColors.textTertiary,
      modifier = Modifier.padding(bottom = 12.dp).size(32.dp),
    )
    Text(
      text = strings.noComments,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textPrimary,
      textAlign = TextAlign.Center,
    )
    Text(
      text = strings.noCommentsHint,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textTertiary,
      textAlign = TextAlign.Center,
    )
  }
}

/**
 * Comment field - Figma `interaction-footer`: the member's avatar, a bg/low
 * pill field and the accent send button, on the container colour.
 */
@Composable
private fun CommentComposer(
  avatarUrl: String?,
  avatarName: String,
  value: String,
  placeholder: String,
  maxLength: Int,
  sending: Boolean,
  uploading: Boolean,
  canSend: Boolean,
  replyToLabel: String?,
  pendingMedia: List<CommunityMedia>,
  imagesEnabled: Boolean,
  canAddPhoto: Boolean,
  photoLabel: String,
  onChange: (String) -> Unit,
  onClearReply: () -> Unit,
  onRemoveMedia: (Int) -> Unit,
  onAddPhoto: () -> Unit,
  onSend: () -> Unit,
) {
  val config = LocalCommunityConfig.current
  val fieldShape = RoundedCornerShape(config.theme.radius.dp.dp)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      // Figma drop shadow 0 -24 20: elevation only casts downward, so the halo
      // above the bar is a fade drawn outside its bounds.
      .drawBehind {
        val height = 24.dp.toPx()
        drawRect(
          brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x14171717)),
            startY = -height,
            endY = 0f,
          ),
          topLeft = Offset(0f, -height),
          size = Size(size.width, height),
        )
      }
      .background(CommunityColors.surface)
      .navigationBarsPadding()
      .imePadding(),
  ) {
    if (replyToLabel != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 20.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = replyToLabel,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = CommunityColors.textTertiary,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClearReply, modifier = Modifier.size(28.dp)) {
          Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = CommunityColors.textTertiary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
    if (pendingMedia.isNotEmpty() || uploading) {
      LazyRow(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(pendingMedia.size) { index ->
          Box {
            AsyncImage(
              model = pendingMedia[index].url,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            )
            Icon(
              Icons.Default.Close,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onRemoveMedia(index) }
                .padding(2.dp),
            )
          }
        }
        if (uploading) {
          item {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CommunityColors.surfaceMuted),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
              )
            }
          }
        }
      }
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      CommunityAvatar(avatarUrl, avatarName.ifBlank { "?" }, size = 32)
      Row(
        modifier = Modifier
          .weight(1f)
          .clip(fieldShape)
          .background(CommunityColors.surfaceMuted)
          .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BasicTextField(
          value = value,
          // The limit applies while typing, not on send: being refused text you
          // have just written is the worst moment to learn it is too long.
          onValueChange = { if (it.length <= maxLength) onChange(it) },
          textStyle = TextStyle(fontSize = 14.sp, color = CommunityColors.textPrimary),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          maxLines = 4,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          modifier = Modifier.weight(1f).padding(vertical = 10.dp),
          decorationBox = { field ->
            Box {
              if (value.isEmpty()) {
                Text(text = placeholder, fontSize = 14.sp, color = CommunityColors.textTertiary)
              }
              field()
            }
          },
        )
        if (imagesEnabled) {
          Icon(
            SolarIcons.Gallery,
            contentDescription = photoLabel,
            tint = CommunityColors.textTertiary.copy(alpha = if (canAddPhoto) 1f else 0.4f),
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .clickable(enabled = canAddPhoto, onClick = onAddPhoto)
              .padding(6.dp),
          )
        }
      }
      Box(
        modifier = Modifier
          .size(38.dp)
          .alpha(if (canSend) 1f else 0.4f)
          .clip(fieldShape)
          .background(accentComposeBrush(config))
          .border(2.dp, Color.White.copy(alpha = 0.1f), fieldShape)
          .clickable(enabled = canSend, onClick = onSend),
        contentAlignment = Alignment.Center,
      ) {
        if (sending) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(
            SolarIcons.ArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

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
    viewModel.createPost(trimmed, groupId, media, poll) { ok ->
      sending = false
      if (ok) {
        onPublished()
        onDone()
      } else {
        errorMessage = strings.loadErrorMessage
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
        imagesEnabled = state.config.features.imagesEnabled,
        canAddPhoto = media.size < maxImages && !uploading,
        showsPoll = showsPoll,
        allowPoll = !isEditing,
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
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (state.profile?.isAnonymous == true) {
        val cardShape = RoundedCornerShape(LocalCommunityConfig.current.theme.radius.dp.dp)
        // Neutral on purpose: an accent or warning fill here competed with the
        // selected category pill right below it.
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CommunityColors.surfaceMuted)
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = strings.composerAnonymousHint,
            style = MaterialTheme.typography.bodySmall,
            color = CommunityColors.textSecondary,
          )
          Text(
            text = strings.composerLeaveAnonymous,
            style = MaterialTheme.typography.labelLarge,
            color = CommunityColors.textPrimary,
            modifier = Modifier
              .clip(cardShape)
              .background(CommunityColors.surface)
              .border(1.dp, CommunityColors.border, cardShape)
              .clickable { openProfileEditor { editingProfile = true } }
              .padding(horizontal = 14.dp, vertical = 8.dp),
          )
        }
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
}

/**
 * Bottom action bar - same chrome as iOS `composerActionBar` / Support InputMessage:
 * photo · video · poll on the left, optional counter, Send on the right.
 */
@Composable
private fun ComposerActionBar(
  strings: CommunityStrings,
  imagesEnabled: Boolean,
  canAddPhoto: Boolean,
  showsPoll: Boolean,
  allowPoll: Boolean = true,
  showsCounter: Boolean,
  remaining: Int,
  canSubmit: Boolean,
  sendLabel: String = strings.publish,
  sendBrush: Brush,
  sendShadow: Color?,
  onAddPhoto: () -> Unit,
  onAddVideo: () -> Unit,
  onTogglePoll: () -> Unit,
  onSend: () -> Unit,
) {
  val radius = LocalCommunityConfig.current.theme.radius.dp
  // Figma create-post (19:4974): no divider, 20 gutters, chips then the pill.
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(CommunityColors.background)
      .navigationBarsPadding()
      .imePadding()
      .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (imagesEnabled) {
      ComposerActionIcon(
        icon = SolarIcons.Gallery,
        label = strings.addPhoto,
        enabled = canAddPhoto,
        onClick = onAddPhoto,
      )
    }
    ComposerActionIcon(
      icon = SolarIcons.Videocamera,
      label = strings.addVideo,
      enabled = canAddPhoto,
      onClick = onAddVideo,
    )
    if (allowPoll) {
      ComposerActionIcon(
        icon = SolarIcons.Chart,
        activeIcon = SolarIcons.ChartBold,
        label = strings.addPoll,
        active = showsPoll,
        onClick = onTogglePoll,
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    if (showsCounter) {
      Text(
        text = "$remaining",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (remaining < 0) {
          MaterialTheme.colorScheme.error
        } else {
          CommunityColors.textTertiary
        },
      )
    }

    val pillShape = RoundedCornerShape(minOf(radius, 16).dp)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .shadow(
          elevation = if (canSubmit && sendShadow != null) 4.dp else 0.dp,
          shape = pillShape,
          ambientColor = if (canSubmit) sendShadow ?: Color.Transparent else Color.Transparent,
          spotColor = if (canSubmit) sendShadow ?: Color.Transparent else Color.Transparent,
        )
        .clip(pillShape)
        .background(sendBrush)
        .alpha(if (canSubmit) 1f else 0.5f)
        .clickable(enabled = canSubmit, onClick = onSend)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Icon(
        imageVector = SolarIcons.Plain,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = sendLabel,
        fontSize = 16.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
  }
}

@Composable
private fun ComposerActionIcon(
  icon: ImageVector,
  label: String,
  enabled: Boolean = true,
  active: Boolean = false,
  activeIcon: ImageVector = icon,
  onClick: () -> Unit,
) {
  // Figma chip (19:5129): bg/low square, half the card radius; the open tool is
  // outlined and switches to the Bold glyph.
  val shape = RoundedCornerShape((LocalCommunityConfig.current.theme.radius.dp / 2).dp)
  Box(
    modifier = Modifier
      .size(32.dp)
      .clip(shape)
      .background(CommunityColors.surfaceMuted)
      .then(if (active) Modifier.border(1.dp, CommunityColors.textPrimary, shape) else Modifier)
      .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = if (active) activeIcon else icon,
      contentDescription = label,
      tint = when {
        active -> CommunityColors.textPrimary
        enabled -> CommunityColors.textTertiary
        else -> CommunityColors.textTertiary.copy(alpha = 0.5f)
      },
      modifier = Modifier.size(16.dp),
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
  onEditPost: (CommunityPost) -> Unit = {},
  onOpenPost: (CommunityPost) -> Unit = {},
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var profile by remember(profileId) { mutableStateOf<CommunityProfile?>(null) }
  var failed by remember(profileId) { mutableStateOf(false) }
  var editing by remember { mutableStateOf(false) }
  var posts by remember(profileId) { mutableStateOf<List<CommunityPost>>(emptyList()) }
  var postsNextCursor by remember(profileId) { mutableStateOf<String?>(null) }
  var postsLoading by remember(profileId) { mutableStateOf(true) }
  var postsLoadingMore by remember(profileId) { mutableStateOf(false) }
  var reportTarget by remember { mutableStateOf<String?>(null) }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  LaunchedEffect(profileId) {
    viewModel.profile(profileId)
      .onSuccess { profile = it }
      .onFailure { failed = true }
    postsLoading = true
    runCatching { viewModel.authorPosts(profileId) }
      .onSuccess { page ->
        posts = page.items
        postsNextCursor = page.nextCursor
      }
      .onFailure { }
    postsLoading = false
  }

  // Same feed page size / cursor chain as the main feed (iOS ProfilePostsStore).
  val shouldLoadMorePosts by remember {
    derivedStateOf {
      val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      val total = listState.layoutInfo.totalItemsCount
      posts.isNotEmpty() && total > 0 && last >= total - 3
    }
  }
  LaunchedEffect(shouldLoadMorePosts, postsLoadingMore, postsNextCursor, posts.size) {
    val cursor = postsNextCursor
    if (!shouldLoadMorePosts || postsLoadingMore || cursor == null) return@LaunchedEffect
    postsLoadingMore = true
    runCatching { viewModel.authorPosts(profileId, cursor) }
      .onSuccess { page ->
        val known = posts.map { it.id }.toSet()
        posts = posts + page.items.filter { it.id !in known }
        postsNextCursor = page.nextCursor
      }
    postsLoadingMore = false
  }

  if (editing && profile?.isMe == true) {
    // Child BackHandler wins over CommunityRoot's feed pop: match the edit
    // screen's in-app back (leave edit, stay on profile).
    BackHandler { editing = false }
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
    containerColor = CommunityColors.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      when {
        failed -> Column(Modifier.fillMaxSize()) {
          Box(Modifier.statusBarsPadding().padding(16.dp)) { BackButton(onBack) }
          CommunityEmptyState(
            icon = Icons.Default.Info,
            title = strings.loadErrorTitle,
            message = strings.loadErrorMessage,
          )
        }

        profile == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }

        else -> profile?.let { member ->
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            item {
              ProfileHeader(
                member = member,
                strings = strings,
                onBack = onBack,
                onEdit = { openProfileEditor { editing = true } },
              )
            }

            if (member.isBanned) {
              item { NoticeBanner(strings.bannedNotice, Modifier.padding(horizontal = 20.dp)) }
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
              else -> {
                items(posts, key = { it.id }) { post ->
                  Box(Modifier.padding(horizontal = 20.dp)) {
                  PostCard(
                    post = post,
                    config = state.config,
                    strings = strings,
                    onOpen = { onOpenPost(post) },
                    onReact = { kind -> viewModel.toggleReaction(post, kind) },
                    onOpenProfile = {},
                    onEdit = { onEditPost(post) },
                    onDelete = { viewModel.deletePost(post.id); posts = posts.filterNot { it.id == post.id } },
                    onReport = { reportTarget = post.id },
                    onVote = { optionId -> viewModel.voteOnPoll(post, optionId) },
                  )
                  }
                }
                if (postsLoadingMore) {
                  item(key = "profile-posts-loading-more") {
                    Box(
                      Modifier.fillMaxWidth().padding(16.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  reportTarget?.let { targetId ->
    ReportSheet(
      strings = strings,
      onDismiss = { reportTarget = null },
      onConfirm = { reason ->
        viewModel.report("post", targetId, reason) {
          reportTarget = null
          viewModel.emit(strings.reportSent)
        }
      },
    )
  }
}

@Composable
private fun EditProfileScreen(
  profile: CommunityProfile,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onBack: () -> Unit,
  onSaved: (CommunityProfile) -> Unit,
  leaveAnonymity: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var nickname by remember { mutableStateOf(if (profile.isAnonymous) "" else profile.nickname) }
  var bio by remember { mutableStateOf(profile.bio.orEmpty()) }
  var isAnonymous by remember {
    mutableStateOf(if (leaveAnonymity) false else profile.isAnonymous)
  }
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
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("empty")
        val (compressed, mime) = ImageCompressor.compress(bytes, ImageCompressor.AvatarMaxSidePx)
        viewModel.uploadMedia(compressed, mime, "avatar.jpg")
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

  val radius = LocalCommunityConfig.current.theme.radius.dp.dp
  val saveBrush = accentPillBrush(LocalCommunityConfig.current)

  // Figma edit-profil (19:4345 / 19:4497).
  Scaffold(
    containerColor = CommunityColors.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TextNavBar(
        title = strings.editProfileTitle,
        leadingLabel = strings.cancel,
        onLeading = onBack,
        containerColor = CommunityColors.background,
        trailing = {
          Text(
            text = strings.save,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
              .clip(RoundedCornerShape(minOf(radius, 12.dp)))
              .background(saveBrush)
              .alpha(if (canSave) 1f else 0.5f)
              .clickable(enabled = canSave, onClick = ::save)
              .padding(horizontal = 12.dp, vertical = 8.dp),
          )
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .navigationBarsPadding()
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
              .size(32.dp)
              .shadow(8.dp, CircleShape, ambientColor = CommunityColors.shadowLow, spotColor = CommunityColors.shadowLow)
              .clip(CircleShape)
              .background(CommunityColors.surface),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              CommunityIcons.Pencil,
              contentDescription = strings.addPhoto,
              tint = CommunityColors.textPrimary,
              modifier = Modifier.size(16.dp),
            )
          }
          if (uploading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
          }
        }
        Text(
          text = nickname.ifBlank { profile.nickname },
          fontSize = 24.sp,
          lineHeight = 24.sp,
          fontWeight = FontWeight.Medium,
          color = CommunityColors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (!isAnonymous) {
        ProfileField(label = strings.nickname, hint = strings.nicknameHelp, radius = radius) {
          ProfileTextField(value = nickname, onValueChange = { nickname = it }, singleLine = true)
        }
      }

      ProfileField(label = strings.bio, counter = "${bio.length}/$bioLimit", radius = radius) {
        ProfileTextField(
          value = bio,
          onValueChange = { if (it.length <= bioLimit) bio = it },
          placeholder = strings.bioPlaceholder,
          minHeight = 118.dp,
        )
      }

      Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = strings.anonymous,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = CommunityColors.textPrimary,
          )
          androidx.compose.material3.Switch(
            checked = isAnonymous,
            onCheckedChange = { isAnonymous = it },
            colors = androidx.compose.material3.SwitchDefaults.colors(
              checkedTrackColor = MaterialTheme.colorScheme.primary,
              uncheckedTrackColor = CommunityColors.surfaceMuted,
              uncheckedBorderColor = CommunityColors.border,
              uncheckedThumbColor = CommunityColors.surface,
            ),
          )
        }
        Text(
          text = strings.anonymousHint,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = CommunityColors.textTertiary,
        )
      }
    }
  }
}

/** Label (and counter or hint) around a borderless container field. */
@Composable
private fun ProfileField(
  label: String,
  radius: androidx.compose.ui.unit.Dp,
  counter: String? = null,
  hint: String? = null,
  field: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textPrimary)
      if (counter != null) {
        Text(counter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CommunityColors.textTertiary)
      }
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(radius))
        .background(CommunityColors.surface)
        .padding(20.dp),
    ) { field() }
    if (hint != null) {
      Text(hint, fontSize = 12.sp, color = CommunityColors.textTertiary)
    }
  }
}

@Composable
private fun ProfileTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String? = null,
  singleLine: Boolean = false,
  minHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = singleLine,
    textStyle = TextStyle(
      fontSize = 14.sp,
      lineHeight = 18.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textPrimary,
    ),
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
    decorationBox = { inner ->
      Box {
        if (value.isEmpty() && placeholder != null) {
          Text(placeholder, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textTertiary)
        }
        inner()
      }
    },
  )
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

/** Matches API `community_media` purpose max size for video/mp4 + video/quicktime. */
private const val MAX_COMMUNITY_VIDEO_BYTES = 75 * 1024 * 1024

/**
 * Figma profile (32:5665): back and menu over a centred avatar and name, then
 * the two stat pills, on a panel with the accent glow and shadow/low.
 */
@Composable
private fun ProfileHeader(
  member: CommunityProfile,
  strings: CommunityStrings,
  onBack: () -> Unit,
  onEdit: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  val glow = accent.copy(alpha = 0.25f)
  var showAvatarViewer by remember(member.id) { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 12.dp)
      .shadow(20.dp, RectangleShape, ambientColor = CommunityColors.shadowLow, spotColor = CommunityColors.shadowLow)
      .background(CommunityColors.background)
      .drawBehind {
        drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), center = Offset.Zero, radius = 240.dp.toPx()))
      }
      .statusBarsPadding()
      .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      BackButton(onBack)
      Spacer(Modifier.weight(1f))
      if (member.isMe) {
        Icon(
          CommunityIcons.Ellipsis,
          contentDescription = strings.editProfile,
          tint = CommunityColors.textTertiary,
          modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onEdit),
        )
      }
    }
    Box(
      modifier = Modifier
        .size(80.dp)
        .border(2.dp, accent.copy(alpha = 0.4f), CircleShape)
        .padding(2.dp)
        .clip(CircleShape)
        .clickable(enabled = !member.avatarUrl.isNullOrBlank() || member.isMe) {
          if (!member.avatarUrl.isNullOrBlank()) showAvatarViewer = true else onEdit()
        },
    ) {
      CommunityAvatar(member.avatarUrl, member.nickname, size = 76)
    }
    if (showAvatarViewer) {
      member.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
        CommunityImageViewer(url = url, onDismiss = { showAvatarViewer = false }, closeLabel = strings.close)
      }
    }
    Row(
      modifier = Modifier.padding(top = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = member.nickname,
        fontSize = 24.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        color = CommunityColors.textPrimary,
      )
      if (member.isTeam) TeamBadge(strings.teamBadge)
    }
    member.bio?.takeIf { it.isNotBlank() }?.let { bioText ->
      Text(
        text = bioText,
        fontSize = 14.sp,
        color = CommunityColors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp, end = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      ProfileStat("${member.postCount}", strings.publications, Modifier.weight(1f))
      if (member.joinedAtMillis > 0) {
        ProfileStat(strings.seniority(member.joinedAtMillis), strings.seniority, Modifier.weight(1f))
      }
    }
  }
}

/** Stat pill: the accent at 16%, the value over its label. */
@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
  val radius = LocalCommunityConfig.current.theme.radius.dp
  Column(
    modifier = modifier
      .clip(RoundedCornerShape((radius / 2).dp))
      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
      .padding(vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CommunityColors.textPrimary)
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textSecondary)
  }
}
