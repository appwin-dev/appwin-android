package io.appwin.community.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityReactionKind
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
  var removedNotice by remember(postId) { mutableStateOf(false) }
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
        pendingMedia = pendingMedia + CommunityMedia(uploaded.publicUrl, uploaded.width, uploaded.height)
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

  /** Deleted by its author, hidden or removed by a moderator. */
  fun removeComment(target: CommunityComment) {
    comments = comments.filterNot { it.id == target.id }.map { root ->
      if (root.replies.none { it.id == target.id }) {
        root
      } else {
        root.copy(
          replies = root.replies.filterNot { it.id == target.id },
          replyCount = (root.replyCount - 1).coerceAtLeast(0),
        )
      }
    }
    viewModel.bumpCommentCount(postId, -1)
    if (target.id == threadRootId) threadRootId = null
  }

  val threadRoot = threadRootId?.let { id -> comments.firstOrNull { it.id == id } }
  val handlers = ActionHandlers(onEdit = onEditPost, onPostGone = { onBack() }, onCommentGone = ::removeComment)
  val commentMenu: @Composable (CommunityComment) -> Unit = { target -> CommentActionsMenu(target, viewModel, strings) }
  val canCompose = features.commentsEnabled && state.allows { it.comments } && state.profile?.isBanned != true
  ReactionFocusHost {
    CommunityActionsHost(viewModel, strings, handlers) {
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
          canCompose = canCompose,
          commentMenu = commentMenu,
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
                  // Removed by moderation as written: the draft stays to rework it.
                  if (it.isRemoved) {
                    removedNotice = true
                    return@onSuccess
                  }
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
      } else {
      Scaffold(
        topBar = {
          Box(Modifier.reactionDimmed()) {
            TextNavBar(title = strings.commentTitle, leadingLabel = strings.back, onLeading = onBack)
          }
        },
        containerColor = CommunityColors.background,
        bottomBar = {
          if (canCompose) Box(Modifier.reactionDimmed()) {
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
                      if (it.isRemoved) {
                        removedNotice = true
                        return@onSuccess
                      }
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
            .reactionDimBehind()
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
                onVote = { optionId -> viewModel.voteOnPoll(it, optionId) },
                actionsMenu = { slot -> PostActionsMenu(it, viewModel, strings, slot) },
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
                  actionsMenu = commentMenu,
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
                      actionsMenu = commentMenu,
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

      }
    }
  }

  if (removedNotice) {
    RemovedByModerationAlert(strings.commentRemovedAlertTitle, strings) {
      removedNotice = false
      // The bell now has a card explaining why.
      viewModel.refreshFeed(silent = true)
    }
  }
}
