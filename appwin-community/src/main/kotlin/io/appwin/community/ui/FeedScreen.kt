package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appwin.community.AppwinCommunity
import io.appwin.community.domain.CommunityPost
import kotlinx.coroutines.flow.distinctUntilChanged


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedScreen(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  listState: LazyListState,
  onClose: (() -> Unit)?,
  onOpenPost: (CommunityPost) -> Unit,
  onOpenOwnProfile: (String) -> Unit,
  onOpenProfile: (String) -> Unit,
  onCompose: () -> Unit,
  onEditPost: (CommunityPost) -> Unit,
  onOpenModeration: () -> Unit,
  onOpenSanctions: () -> Unit,
) = ReactionFocusHost {
  CommunityActionsHost(
    viewModel = viewModel,
    strings = strings,
    handlers = ActionHandlers(onEdit = onEditPost, onPostGone = viewModel::removePostLocally),
  ) {
    FeedContent(
      viewModel, strings, listState, onClose, onOpenPost, onOpenOwnProfile, onOpenProfile, onCompose,
      onOpenModeration, onOpenSanctions,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedContent(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  listState: LazyListState,
  onClose: (() -> Unit)?,
  onOpenPost: (CommunityPost) -> Unit,
  onOpenOwnProfile: (String) -> Unit,
  onOpenProfile: (String) -> Unit,
  onCompose: () -> Unit,
  onOpenModeration: () -> Unit,
  onOpenSanctions: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  // LazyColumn anchors on item keys: prepending new posts would keep the old
  // first visible card on screen. Jump to top once pull-to-refresh finishes.
  var wasRefreshing by remember { mutableStateOf(false) }
  LaunchedEffect(state.isRefreshing) {
    if (wasRefreshing && !state.isRefreshing) {
      listState.scrollToItem(0)
    }
    wasRefreshing = state.isRefreshing
  }

  // Pagination driven by scroll position rather than a button:
  // `derivedStateOf` avoids recomposing the screen on every pixel scrolled.
  // Keys include isLoadingMore / nextCursor / posts.size so chaining continues
  // when the list stays near the end after a page loads (short viewport).
  val shouldLoadMore by remember {
    derivedStateOf {
      val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      state.posts.isNotEmpty() && last >= state.posts.size - 3
    }
  }
  LaunchedEffect(shouldLoadMore, state.isLoadingMore, state.nextCursor, state.posts.size) {
    if (shouldLoadMore && !state.isLoadingMore && state.nextCursor != null) {
      viewModel.loadMore()
    }
  }

  // Views follow what is actually on screen. `snapshotFlow` re-emits on
  // scroll/layout; a one-shot LaunchedEffect often ran before the first layout
  // and never again, so a pinned post at the top could stay at 1 forever.
  LaunchedEffect(listState) {
    snapshotFlow {
      listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
    }
      .distinctUntilChanged()
      .collect { visible -> viewModel.trackVisible(visible) }
  }

  val event by viewModel.events.collectAsStateWithLifecycle()
  LaunchedEffect(event) {
    event?.let {
      snackbar.showSnackbar(it)
      viewModel.consumeEvent()
    }
  }

  // Header lives in the column (not Scaffold topBar): inside a React Native
  // ComposeView the topBar slot often fails to push content down, so the list's
  // top contentPadding sits under the header and the first post looks flush.
  // Same structure as iOS (header then list), works for native hosts too.
  // Flutter/RN tab hosts already clear the system nav; padding again leaves a
  // dark gap under the compose FAB (cf. LocalHostOwnsBottomChrome).
  Scaffold(
    contentWindowInsets = communityBottomInsets(),
    floatingActionButton = {
      if (state.canPostHere) {
        // Scaffold already lifts FAB by 16dp; iOS wants 24 above the safe area.
        Box(modifier = Modifier.padding(bottom = 8.dp).reactionDimmed(state.config.radiusCard)) {
          ComposeButton(
            label = strings.newPost,
            brush = accentComposeBrush(state.config),
            shadow = accentShadowColor(state.config),
            onClick = onCompose,
          )
        }
      }
    },
    // Figma: the compose pill is centred at the bottom, not a corner FAB.
    floatingActionButtonPosition = FabPosition.Center,
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      // Figma feed header (5:1983) / iOS screenHeader: bg/container (surface),
      // accent glow top-left, soft drop shadow so the list reads as a
      // separate plane. Page colour alone melts the header into the feed on
      // Android (Compose elevation is weaker than SwiftUI's compositingGroup).
      val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
      Column(
        modifier = Modifier
          .zIndex(1f)
          .reactionDimmed()
          .shadow(
            elevation = 12.dp,
            shape = RectangleShape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.14f),
          )
          .background(CommunityColors.surface)
          .drawBehind {
            // Approximate iOS CommunityAccentGlow: 320×240 ellipse at 24%.
            drawRect(
              Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset.Zero,
                radius = 280.dp.toPx(),
              ),
            )
          },
      ) {
        FeedHeader(
          title = feedTitle(state.config),
          closeLabel = strings.close,
          profileLabel = strings.profile,
          profile = state.profile,
          onClose = onClose,
          onOpenProfile = {
            state.profile?.id?.takeIf { it.isNotEmpty() }?.let(onOpenOwnProfile)
          },
          unreadSanctionCount = state.unreadSanctionCount,
          moderationPendingCount = state.moderationPendingCount.takeIf { state.profile?.canModerate == true },
          onOpenSanctions = onOpenSanctions,
          onOpenModeration = onOpenModeration,
          strings = strings,
        )

        if (state.groups.size > 1) {
          GroupTabs(
            groups = state.groups,
            selectedId = state.selectedGroupId,
            allLabel = strings.allGroups,
            brush = accentPillBrush(state.config),
            onSelect = viewModel::selectGroup,
          )
        } else {
          // iOS clears 16pt under the header when there are no group pills.
          Spacer(modifier = Modifier.height(16.dp))
        }
      }

      val debugUnlocked by AppwinCommunity.debugUnlocked.collectAsStateWithLifecycle()
      if (debugUnlocked) {
        CommunityDebugAlert(DEBUG_UNLOCKED_INFO, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
      }

      if (state.profile?.isBanned == true) {
        NoticeBanner(
          strings.bannedNotice,
          // Clears the « Épinglé » badge that rides on the first card.
          Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = if (state.posts.firstOrNull()?.isPinned == true) 12.dp else 0.dp),
          danger = true,
        )
      }

      // Same gesture as iOS `.refreshable` on the feed list.
      // Always keep a LazyColumn: a non-scrollable empty Column does not
      // participate in nested scroll, so PullToRefreshBox never sees the drag.
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        PullToRefreshBox(
          isRefreshing = state.isRefreshing,
          onRefresh = viewModel::refreshFeed,
          modifier = Modifier.fillMaxSize(),
        ) {
          val isEmpty = state.posts.isEmpty()
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().reactionDimBehind(),
            contentPadding = if (isEmpty) {
              PaddingValues(0.dp)
            } else {
              PaddingValues(
                start = 20.dp,
                end = 20.dp,
                // Match iOS FeedView list top / FAB clearance.
                top = 20.dp,
                bottom = if (state.canPostHere) 104.dp else 24.dp,
              )
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (isEmpty) {
              item(key = "feed-empty") {
                CommunityEmptyState(
                  icon = Icons.Default.Info,
                  title = strings.emptyFeedTitle,
                  message = strings.emptyFeedMessage,
                  modifier = Modifier.fillParentMaxSize(),
                )
              }
            } else {
              items(state.posts, key = { it.id }) { post ->
                PostCard(
                  post = post,
                  config = state.config,
                  strings = strings,
                  onOpen = { onOpenPost(post) },
                  onReact = { viewModel.toggleReaction(post, it) },
                  onOpenProfile = onOpenProfile,
                  onVote = { optionId -> viewModel.voteOnPoll(post, optionId) },
                  actionsMenu = { slot -> PostActionsMenu(post, viewModel, strings, slot) },
                )
              }
              if (state.isLoadingMore) {
                item {
                  Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                  ) { CircularProgressIndicator(modifier = Modifier.padding(4.dp)) }
                }
              }
            }
          }
        }
        // Soft falloff under the header (iOS compositingGroup shadow). Drawn
        // over the list so a Column sibling cannot cover Modifier.shadow.
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .align(Alignment.TopCenter)
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent),
              ),
            ),
        )
      }
    }
  }
}
