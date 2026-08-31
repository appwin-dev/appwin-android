package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appwin.community.domain.CommunityPost

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
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val strings = remember(context) { CommunityStrings(context) }
  var route by remember { mutableStateOf<CommunityRoute>(CommunityRoute.Feed) }

  CommunityTheme(state.config) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      when {
        state.isLoading -> LoadingScreen()

        state.loadFailed -> CommunityEmptyState(
          icon = Icons.Default.Info,
          title = strings.loadErrorTitle,
          message = strings.loadErrorMessage,
          actionLabel = strings.retry,
          onAction = viewModel::load,
        )

        // Community switched off studio-side: a waiting screen, not an empty
        // feed. The server serves no content in that state anyway.
        !state.config.features.enabled -> CommunityEmptyState(
          icon = Icons.Default.Info,
          title = strings.disabledTitle,
          message = strings.disabledMessage,
        )

        else -> when (val current = route) {
          CommunityRoute.Feed -> FeedScreen(
            viewModel = viewModel,
            strings = strings,
            onClose = onClose,
            onOpenPost = { route = CommunityRoute.Post(it.id) },
            onOpenProfile = { route = CommunityRoute.Profile(it) },
            onCompose = { route = CommunityRoute.Composer },
          )

          is CommunityRoute.Post -> PostDetailScreen(
            postId = current.postId,
            viewModel = viewModel,
            strings = strings,
            onBack = { route = CommunityRoute.Feed },
            onOpenProfile = { route = CommunityRoute.Profile(it) },
          )

          is CommunityRoute.Profile -> ProfileScreen(
            profileId = current.profileId,
            viewModel = viewModel,
            strings = strings,
            onBack = { route = CommunityRoute.Feed },
          )

          CommunityRoute.Composer -> ComposerScreen(
            viewModel = viewModel,
            strings = strings,
            onDone = { route = CommunityRoute.Feed },
          )
        }
      }
    }
  }
}

private sealed interface CommunityRoute {
  data object Feed : CommunityRoute
  data object Composer : CommunityRoute
  data class Post(val postId: String) : CommunityRoute
  data class Profile(val profileId: String) : CommunityRoute
}

@Composable
private fun LoadingScreen() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
    CircularProgressIndicator()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedScreen(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onClose: (() -> Unit)?,
  onOpenPost: (CommunityPost) -> Unit,
  onOpenProfile: (String) -> Unit,
  onCompose: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val snackbar = remember { SnackbarHostState() }
  var reportTarget by remember { mutableStateOf<String?>(null) }

  // Pagination driven by scroll position rather than a button:
  // `derivedStateOf` avoids recomposing the screen on every pixel scrolled.
  val shouldLoadMore by remember {
    derivedStateOf {
      val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= state.posts.size - 3
    }
  }
  LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

  // Views are reported for what is actually on screen; each post is counted
  // once per session, in the ViewModel.
  LaunchedEffect(listState, state.posts) {
    val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
    viewModel.trackVisible(visible)
  }

  val event by viewModel.events.collectAsStateWithLifecycle()
  LaunchedEffect(event) {
    event?.let {
      snackbar.showSnackbar(it)
      viewModel.consumeEvent()
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(state.config.context.projectName.ifBlank { strings.title }) },
        navigationIcon = {
          if (onClose != null) {
            IconButton(onClick = onClose) {
              Icon(Icons.Default.Close, contentDescription = strings.close)
            }
          }
        },
      )
    },
    floatingActionButton = {
      if (state.canPostHere) {
        FloatingActionButton(onClick = onCompose) {
          Icon(Icons.Default.Add, contentDescription = strings.newPost)
        }
      }
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (state.groups.size > 1) {
        GroupTabs(
          groups = state.groups,
          selectedId = state.selectedGroupId,
          allLabel = strings.allGroups,
          onSelect = viewModel::selectGroup,
        )
      }

      if (state.profile?.isBanned == true) {
        NoticeBanner(strings.bannedNotice, Modifier.padding(horizontal = 12.dp))
      }

      if (state.posts.isEmpty() && !state.isRefreshing) {
        CommunityEmptyState(
          icon = Icons.Default.Info,
          title = strings.emptyFeedTitle,
          message = strings.emptyFeedMessage,
        )
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(vertical = 6.dp, horizontal = 0.dp),
          verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
          items(state.posts, key = { it.id }) { post ->
            PostCard(
              post = post,
              config = state.config,
              strings = strings,
              onOpen = { onOpenPost(post) },
              onReact = { viewModel.toggleReaction(post, it) },
              onOpenProfile = onOpenProfile,
              onDelete = { viewModel.deletePost(post.id) },
              onReport = { reportTarget = post.id },
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
private fun GroupTabs(
  groups: List<io.appwin.community.domain.CommunityGroup>,
  selectedId: String?,
  allLabel: String,
  onSelect: (String?) -> Unit,
) {
  LazyRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      FilterChip(
        selected = selectedId == null,
        onClick = { onSelect(null) },
        label = { Text(allLabel) },
      )
    }
    items(groups, key = { it.id }) { group ->
      FilterChip(
        selected = selectedId == group.id,
        onClick = { onSelect(group.id) },
        label = { Text(listOfNotNull(group.emoji, group.name).joinToString(" ")) },
      )
    }
  }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
  IconButton(onClick = onBack) {
    Icon(Icons.Default.ArrowBack, contentDescription = null)
  }
}
