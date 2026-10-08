package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityFeatures
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityReactionKind
import kotlinx.coroutines.delay

/** The dedicated « Réponses » screen of one comment thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplyThreadScreen(
  root: CommunityComment,
  strings: CommunityStrings,
  features: CommunityFeatures,
  commentMaxLength: Int,
  draft: String,
  sending: Boolean,
  uploading: Boolean,
  canSend: Boolean,
  canCompose: Boolean,
  commentMenu: @Composable (CommunityComment) -> Unit,
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
    topBar = {
      Box(Modifier.reactionDimmed()) { TextNavBar(title = strings.replies, leadingLabel = strings.back, onLeading = onBack) }
    },
    containerColor = CommunityColors.background,
    bottomBar = {
      if (canCompose) Box(Modifier.reactionDimmed()) {
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
        .padding(padding)
        .reactionDimBehind(),
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
          actionsMenu = commentMenu,
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
          actionsMenu = commentMenu,
        )
      }
    }
  }
}
