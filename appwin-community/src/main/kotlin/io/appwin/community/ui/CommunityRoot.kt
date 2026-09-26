package io.appwin.community.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.push.registerCommunityPush
import io.appwin.community.push.unregisterCommunityPush
import kotlinx.coroutines.flow.distinctUntilChanged

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
  val strings = remember(context) { communityStrings(context) }
  var route by remember { mutableStateOf<CommunityRoute>(CommunityRoute.Feed) }
  val backToFeed = { route = CommunityRoute.Feed }
  // Owned here, not in FeedScreen: the exclusive route `when` leaves FeedScreen
  // composition on Post/Profile, which would reset a local LazyListState to top.
  val feedListState = rememberLazyListState()

  // Push deeplink (cold start or tap): open post / replies once the feed is up.
  DisposableEffect(Unit) {
    registerCommunityPush { target ->
      route = CommunityRoute.Post(postId = target.postId, threadCommentId = target.threadCommentId)
    }
    onDispose { unregisterCommunityPush() }
  }

  // Same as Support messenger: system / gesture back must match the in-app
  // back affordance, otherwise the host Activity finishes and Community exits.
  // Skip when no dispatcher owner (plain Activity hosts / misconfigured
  // PlatformViews): BackHandler would throw and abort Flutter across JNI.
  if (LocalOnBackPressedDispatcherOwner.current != null) {
    BackHandler(enabled = route != CommunityRoute.Feed) { backToFeed() }
  }

  CommunityTheme(state.config) {
    // Background goes edge-to-edge; each screen pads for status / nav bars
    // itself (custom FeedHeader, Material TopAppBar, composers).
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      when {
        state.isLoading -> LoadingScreen()

        state.loadFailed -> Column(modifier = Modifier.fillMaxSize()) {
          FeedHeader(
            title = state.config.theme.headerTitle?.takeIf { it.isNotBlank() }
              ?: state.config.context.projectName.ifBlank { strings.title },
            showTitle = state.config.theme.headerTitleVisible,
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
            title = state.config.theme.headerTitle?.takeIf { it.isNotBlank() }
              ?: state.config.context.projectName.ifBlank { strings.title },
            showTitle = state.config.theme.headerTitleVisible,
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
          )

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
            onDone = {
              val editedId = current.editing?.id
              route = if (editedId != null) CommunityRoute.Post(editedId) else CommunityRoute.Feed
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedScreen(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  listState: LazyListState,
  onClose: (() -> Unit)?,
  onOpenPost: (CommunityPost) -> Unit,
  onOpenOwnProfile: (String) -> Unit,
  onOpenProfile: (String) -> Unit,
  onCompose: () -> Unit,
  onEditPost: (CommunityPost) -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var reportTarget by remember { mutableStateOf<String?>(null) }
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
  Scaffold(
    contentWindowInsets = WindowInsets.navigationBars,
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
      FeedHeader(
        title = state.config.theme.headerTitle?.takeIf { it.isNotBlank() }
          ?: state.config.context.projectName.ifBlank { strings.title },
        showTitle = state.config.theme.headerTitleVisible,
        closeLabel = strings.close,
        profileLabel = strings.profile,
        profile = state.profile,
        onClose = onClose,
        onOpenProfile = {
          state.profile?.id?.takeIf { it.isNotEmpty() }?.let(onOpenOwnProfile)
        },
      )

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

      // Gap above the first card (iOS `.padding(.top, 12)`). Kept as a real
      // Spacer rather than only LazyColumn contentPadding: inside RN's
      // ComposeView, list contentPadding under a Scaffold has been clipped.
      if (state.posts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
      }

      // Same gesture as iOS `.refreshable` on the feed list.
      // Always keep a LazyColumn: a non-scrollable empty Column does not
      // participate in nested scroll, so PullToRefreshBox never sees the drag.
      PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refreshFeed,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        val isEmpty = state.posts.isEmpty()
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = if (isEmpty) {
            PaddingValues(0.dp)
          } else {
            PaddingValues(
              start = 16.dp,
              end = 16.dp,
              top = 0.dp,
              // The floating button must not hide the last post.
              bottom = if (state.canPostHere) 96.dp else 24.dp,
            )
          },
          verticalArrangement = Arrangement.spacedBy(12.dp),
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
                onEdit = { onEditPost(post) },
                onDelete = { viewModel.deletePost(post.id) },
                onReport = { reportTarget = post.id },
                onVote = { optionId -> viewModel.voteOnPoll(post, optionId) },
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
  profileLabel: String,
  profile: io.appwin.community.domain.CommunityProfile?,
  onClose: (() -> Unit)?,
  onOpenProfile: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(CommunityColors.background)
      // Custom header (not Material TopAppBar) must pad the status bar itself
      // so the title / avatar sit below the cutout on edge-to-edge windows.
      .statusBarsPadding(),
  ) {
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
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          color = CommunityColors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      } else {
        Spacer(modifier = Modifier.weight(1f))
      }
      if (onClose != null) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClose),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = closeLabel,
            tint = CommunityColors.textPrimary,
            modifier = Modifier.size(17.dp),
          )
        }
      }
      // Same trailing slot as iOS: member avatar opens their own profile.
      // Always render (placeholder before bootstrap) so RN/Flutter match native.
      val headerProfile = profile ?: CommunityProfile.Placeholder
      CommunityAvatar(
        url = headerProfile.avatarUrl,
        fallbackText = headerProfile.nickname.ifBlank { "?" },
        size = 28,
        modifier = Modifier
          .clip(CircleShape)
          .clickable(
            enabled = headerProfile.id.isNotEmpty(),
            onClick = onOpenProfile,
            onClickLabel = profileLabel,
          ),
      )
    }
    HorizontalDivider(thickness = 1.dp, color = CommunityColors.border)
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
