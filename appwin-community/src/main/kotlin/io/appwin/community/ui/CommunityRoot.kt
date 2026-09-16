package io.appwin.community.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
      FeedHeader(
        title = state.config.theme.headerTitle?.takeIf { it.isNotBlank() }
          ?: state.config.context.projectName.ifBlank { strings.title },
        showTitle = state.config.theme.headerTitleVisible,
        closeLabel = strings.close,
        onClose = onClose,
      )
    },
    floatingActionButton = {
      if (state.canPostHere) {
        ComposeButton(
          label = strings.newPost,
          brush = accentComposeBrush(state.config),
          shadow = accentShadowColor(state.config),
          onClick = onCompose,
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (state.groups.size > 1) {
        GroupTabs(
          groups = state.groups,
          selectedId = state.selectedGroupId,
          allLabel = strings.allGroups,
          brush = accentPillBrush(state.config),
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
          contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            // The floating button must not hide the last post.
            bottom = if (state.canPostHere) 96.dp else 24.dp,
          ),
          verticalArrangement = Arrangement.spacedBy(12.dp),
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

/**
 * Screen header - Figma `screen-header` (2120:24338).
 *
 * A large bold title flush with the feed, not a centred `TopAppBar` title: the
 * community is a full-screen tab of the host app, and Material's app bar reads
 * as a chrome the feed does not have in the mock.
 */
@Composable
private fun FeedHeader(
  title: String,
  showTitle: Boolean,
  closeLabel: String,
  onClose: (() -> Unit)?,
) {
  Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Hiding the title drops the text alone: the row still carries the feed's
      // actions, and taking those away with it would leave the member no way
      // out of a screen opened as an activity.
      if (showTitle) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      } else {
        Spacer(Modifier.weight(1f))
      }
      if (onClose != null) {
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Close, contentDescription = closeLabel)
        }
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/**
 * Compose button - Figma `floating-compose-button` (2120:24440): a labelled
 * pill on the accent gradient, not a bare circular FAB.
 */
@Composable
private fun ComposeButton(
  label: String,
  brush: Brush,
  shadow: Color,
  onClick: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      // Coloured shadow needs API 28; below that Android draws its default
      // grey one, which is the right degradation - never an orange halo.
      .shadow(8.dp, CircleShape, ambientColor = shadow, spotColor = shadow)
      .clip(CircleShape)
      .background(brush)
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 14.dp),
  ) {
    Icon(
      Icons.Default.Add,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onPrimary,
    )
  }
}

/**
 * Group pills - Figma `filter-pills-row` (2120:24351).
 *
 * Hand-rolled rather than `FilterChip`: the mock fills the active pill with the
 * accent gradient, and a Material chip only takes a flat container colour.
 */
@Composable
private fun GroupTabs(
  groups: List<io.appwin.community.domain.CommunityGroup>,
  selectedId: String?,
  allLabel: String,
  brush: Brush,
  onSelect: (String?) -> Unit,
) {
  LazyRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      GroupPill(allLabel, selectedId == null, brush) { onSelect(null) }
    }
    items(groups, key = { it.id }) { group ->
      GroupPill(
        label = listOfNotNull(group.emoji, group.name).joinToString(" "),
        selected = selectedId == group.id,
        brush = brush,
      ) { onSelect(group.id) }
    }
  }
}

@Composable
private fun GroupPill(
  label: String,
  selected: Boolean,
  brush: Brush,
  onClick: () -> Unit,
) {
  val base = Modifier.clip(CircleShape)
  val filled = if (selected) {
    base.background(brush)
  } else {
    base
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
  }
  Text(
    text = label,
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    maxLines = 1,
    modifier = filled
      .clickable(onClick = onClick)
      .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 8.dp),
  )
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
  IconButton(onClick = onBack) {
    Icon(Icons.Default.ArrowBack, contentDescription = null)
  }
}
