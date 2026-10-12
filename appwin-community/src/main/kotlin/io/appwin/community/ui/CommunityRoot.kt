package io.appwin.community.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appwin.community.AppwinCommunityPostTarget
import io.appwin.community.domain.CommunityPost
import io.appwin.community.push.CommunityFeedPresence

/**
 * Racine du fil communautaire.
 *
 * Navigation by hand rather than a library: three stacked screens, in a view the
 * host app can embed anywhere. A navigation graph would impose its dependency -
 * and its version conflicts - on every integrating app.
 */
@Composable
internal fun CommunityRoot(
  viewModel: CommunityViewModel = viewModel(),
  onClose: (() -> Unit)? = null,
  initialTarget: AppwinCommunityPostTarget? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val strings = remember(context) { communityStrings(context) }
  // Opened on a post (notification modal): that post is home, and leaving it
  // closes the modal rather than revealing a feed the member never asked for.
  val home: CommunityRoute = remember(initialTarget) {
    initialTarget?.let { CommunityRoute.Post(it.postId, it.commentId) } ?: CommunityRoute.Feed
  }
  var route by remember { mutableStateOf(home) }
  val backToFeed = {
    if (route == home && home != CommunityRoute.Feed && onClose != null) onClose() else route = home
  }
  // Owned here, not in FeedScreen: the exclusive route `when` leaves FeedScreen
  // composition on Post/Profile, which would reset a local LazyListState to top.
  val feedListState = rememberLazyListState()
  // Bumped on publish: the new post lands at the top, out of sight when the
  // member had scrolled down.
  var publishedTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(publishedTick) {
    if (publishedTick > 0) feedListState.scrollToItem(0)
  }

  // Push taps and openPost land here while this feed is mounted; the route
  // survives the loading state, so the post opens once the feed can show it.
  val view = LocalView.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(view, lifecycle) {
    val feed = CommunityFeedPresence.Feed(
      isVisible = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && view.isShown },
      open = { target -> route = CommunityRoute.Post(target.postId, target.commentId) },
    )
    CommunityFeedPresence.register(feed)
    onDispose { CommunityFeedPresence.unregister(feed) }
  }

  // Suppress the in-app banner while this post's detail is already on screen.
  DisposableEffect(route) {
    val post = route as? CommunityRoute.Post
    OpenPost.postId = post?.postId
    onDispose {
      if (OpenPost.postId == post?.postId) OpenPost.postId = null
    }
  }

  // Same as Support messenger: system / gesture back must match the in-app
  // back affordance, otherwise the host Activity finishes and Community exits.
  // Skip when no dispatcher owner (plain Activity hosts / misconfigured
  // PlatformViews): BackHandler would throw and abort Flutter across JNI.
  if (LocalOnBackPressedDispatcherOwner.current != null) {
    BackHandler(enabled = route != home) { backToFeed() }
  }

  CommunityTheme(state.config) {
    // Background goes edge-to-edge; each screen pads for status / nav bars
    // itself (custom FeedHeader, Material TopAppBar, composers).
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      when {
        state.isLoading -> LoadingScreen()

        state.loadFailed -> Column(modifier = Modifier.fillMaxSize()) {
          FeedHeader(
            title = feedTitle(state.config),
            closeLabel = strings.close,
            profileLabel = strings.profile,
            profile = state.profile,
            onClose = onClose,
            onOpenProfile = { state.profile?.id?.let { route = CommunityRoute.Profile(it) } },
          )
          CommunityEmptyState(
            icon = Icons.Default.Info,
            title = strings.loadErrorTitle,
            message = strings.loadErrorMessage,
            actionLabel = strings.retry,
            onAction = viewModel::load,
            modifier = Modifier.weight(1f),
          )
        }

        // Community switched off studio-side: a waiting screen, not an empty
        // feed. The server serves no content in that state anyway.
        !state.config.features.enabled -> Column(modifier = Modifier.fillMaxSize()) {
          FeedHeader(
            title = feedTitle(state.config),
            closeLabel = strings.close,
            profileLabel = strings.profile,
            profile = state.profile,
            onClose = onClose,
            onOpenProfile = { state.profile?.id?.let { route = CommunityRoute.Profile(it) } },
          )
          CommunityEmptyState(
            icon = Icons.Default.Info,
            title = strings.disabledTitle,
            message = strings.disabledMessage,
            modifier = Modifier.weight(1f),
          )
        }

        else -> when (val current = route) {
          CommunityRoute.Feed -> FeedScreen(
            viewModel = viewModel,
            strings = strings,
            listState = feedListState,
            onClose = onClose,
            onOpenPost = { route = CommunityRoute.Post(it.id) },
            onOpenOwnProfile = { profileId ->
              // Own header avatar: always open (iOS parity), even if browsing
              // other profiles is disabled.
              route = CommunityRoute.Profile(profileId)
            },
            onOpenProfile = { profileId ->
              if (state.config.features.profilesEnabled) {
                route = CommunityRoute.Profile(profileId)
              }
            },
            onCompose = { route = CommunityRoute.Composer() },
            onEditPost = { route = CommunityRoute.Composer(editing = it) },
            onOpenModeration = { route = CommunityRoute.Moderation },
            onOpenSanctions = { route = CommunityRoute.Sanctions },
          )

          CommunityRoute.Moderation -> ModerationScreen(viewModel, strings, onBack = backToFeed)

          CommunityRoute.Sanctions -> SanctionsScreen(viewModel, strings, onBack = backToFeed)

          is CommunityRoute.Post -> PostDetailScreen(
            postId = current.postId,
            viewModel = viewModel,
            strings = strings,
            onBack = backToFeed,
            onOpenProfile = { profileId ->
              if (state.config.features.profilesEnabled) {
                route = CommunityRoute.Profile(profileId)
              }
            },
            onEditPost = { route = CommunityRoute.Composer(editing = it) },
            initialThreadCommentId = current.threadCommentId,
            fromPushDeeplink = current.threadCommentId != null,
          )

          is CommunityRoute.Profile -> ProfileScreen(
            profileId = current.profileId,
            viewModel = viewModel,
            strings = strings,
            onBack = backToFeed,
            onCompose = { route = CommunityRoute.Composer() },
            onEditPost = { route = CommunityRoute.Composer(editing = it) },
            onOpenPost = { route = CommunityRoute.Post(it.id) },
          )

          is CommunityRoute.Composer -> ComposerScreen(
            viewModel = viewModel,
            strings = strings,
            editingPost = current.editing,
            onPublished = { publishedTick++ },
            onDone = {
              val editedId = current.editing?.id
              route = if (editedId != null) CommunityRoute.Post(editedId) else home
            },
          )
        }
      }
    }
  }
}

private sealed interface CommunityRoute {
  data object Feed : CommunityRoute
  data class Composer(val editing: CommunityPost? = null) : CommunityRoute
  data class Post(
    val postId: String,
    val threadCommentId: String? = null,
  ) : CommunityRoute
  data class Profile(val profileId: String) : CommunityRoute
  data object Moderation : CommunityRoute
  data object Sanctions : CommunityRoute
}

@Composable
private fun LoadingScreen() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding(),
    contentAlignment = androidx.compose.ui.Alignment.Center,
  ) {
    CircularProgressIndicator()
  }
}
