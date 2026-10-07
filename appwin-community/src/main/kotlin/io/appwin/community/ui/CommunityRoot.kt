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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appwin.community.AppwinCommunity
import io.appwin.community.AppwinCommunityPostTarget
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.push.CommunityFeedPresence
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
  // Flutter/RN tab hosts already clear the system nav; padding again leaves a
  // dark gap under the compose FAB (cf. LocalHostOwnsBottomChrome).
  Scaffold(
    contentWindowInsets = communityBottomInsets(),
    floatingActionButton = {
      if (state.canPostHere) {
        // Scaffold already lifts FAB by 16dp; iOS wants 24 above the safe area.
        Box(modifier = Modifier.padding(bottom = 8.dp)) {
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
        NoticeBanner(strings.bannedNotice, Modifier.padding(horizontal = 12.dp), danger = true)
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
            modifier = Modifier.fillMaxSize(),
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
 * The studio's feed title, or none: a blank `headerTitle` shows no title at all
 * (no project-name fallback), and `headerTitleVisible` hides it.
 */
private fun feedTitle(config: io.appwin.community.domain.CommunityConfig): String? =
  config.theme.headerTitle?.trim()?.takeIf { it.isNotEmpty() && config.theme.headerTitleVisible }

/**
 * Screen header - Figma feed `header` (5:1983): 32sp title and the member's
 * avatar on bg/container, no divider (the list scrolls under the elevated bar).
 */
@Composable
private fun FeedHeader(
  title: String?,
  closeLabel: String,
  profileLabel: String,
  profile: io.appwin.community.domain.CommunityProfile?,
  onClose: (() -> Unit)?,
  onOpenProfile: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      // Custom header (not Material TopAppBar) must pad the status bar itself
      // so the title / avatar sit below the cutout on edge-to-edge windows.
      .statusBarsPadding()
      .padding(start = 20.dp, end = 20.dp, top = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (title != null) {
      Text(
        text = title,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Medium,
        color = CommunityColors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
    } else {
      Spacer(modifier = Modifier.weight(1f))
    }
    if (onClose != null) {
      GlassIconButton(Icons.Default.Close, contentDescription = closeLabel, onClick = onClose)
    }
    // Same trailing slot as iOS: member avatar opens their own profile.
    // Always render (placeholder before bootstrap) so RN/Flutter match native.
    val headerProfile = profile ?: CommunityProfile.Placeholder
    CommunityAvatar(
      url = headerProfile.avatarUrl,
      fallbackText = headerProfile.nickname.ifBlank { "?" },
      size = 40,
      modifier = Modifier
        .clip(CircleShape)
        .clickable(
          enabled = headerProfile.id.isNotEmpty(),
          onClick = onOpenProfile,
          onClickLabel = profileLabel,
        ),
    )
  }
}

/**
 * Compose button - Figma "Créer un post" (5:1999): accent pill, white 12%
 * ring, pen glyph, lifted by the neutral shadow/high.
 */
@Composable
private fun ComposeButton(
  label: String,
  brush: Brush,
  shadow: Color?,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(LocalCommunityConfig.current.radiusCard)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      .height(56.dp)
      // Figma 0 24 40: a tall, soft halo. Tinted shadows need API 28; below it
      // Android draws its default grey one, which is close enough.
      .then(
        if (shadow != null) {
          Modifier.shadow(24.dp, shape, ambientColor = shadow, spotColor = shadow)
        } else {
          Modifier
        },
      )
      .clip(shape)
      .background(brush)
      .border(3.dp, Color.White.copy(alpha = 0.12f), shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp),
  ) {
    Icon(
      SolarIcons.PenNewSquare,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = label,
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onPrimary,
    )
  }
}

/**
 * Group pills - Figma `toggleGroup` (5:1989): the active pill on the accent
 * gradient, the others outlined.
 *
 * Hand-rolled rather than `FilterChip`: a Material chip only takes a flat
 * container colour.
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
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(20.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
  // Same radius token as the cards: LOW gives soft rectangles, MAX capsules.
  val shape = RoundedCornerShape(LocalCommunityConfig.current.radiusCard)
  val base = Modifier.clip(shape)
  val filled = if (selected) {
    base.background(brush)
  } else {
    base.border(1.dp, CommunityColors.border, shape)
  }
  Text(
    text = label,
    fontSize = 14.sp,
    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
    color = if (selected) MaterialTheme.colorScheme.onPrimary else CommunityColors.textTertiary,
    maxLines = 1,
    modifier = filled
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp),
  )
}

/**
 * Figma button/icon-native: translucent surface, soft halo, 17dp glyph (iOS).
 */
@Composable
internal fun GlassIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(38.dp)
      .shadow(20.dp, CircleShape, ambientColor = GlassHalo, spotColor = GlassHalo)
      .clip(CircleShape)
      .background(CommunityColors.surface.copy(alpha = 0.56f))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = CommunityColors.textPrimary,
      modifier = Modifier.size(17.dp),
    )
  }
}

private val GlassHalo = Color.Black.copy(alpha = 0.12f)

/**
 * Bar of the pushed screens - Figma `header/default` (19:5365): a subtle text
 * action on the left ("Retour", "Annuler"), the title centred, an optional
 * trailing action, on the container colour.
 */
@Composable
internal fun TextNavBar(
  title: String,
  leadingLabel: String,
  onLeading: () -> Unit,
  trailing: (@Composable () -> Unit)? = null,
  /** The comments bar sits on the container; the composer and editor on the page. */
  containerColor: Color = CommunityColors.surface,
) {
  // 14sp and 16sp labels centred vertically sit on different baselines (Figma
  // aligns them on the text baseline); equal-weight side slots keep the title
  // centred whatever the leading and trailing widths.
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(containerColor)
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 24.dp),
  ) {
    Box(modifier = Modifier.weight(1f).alignByBaseline()) {
      Text(
        text = leadingLabel,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = CommunityColors.textTertiary,
        modifier = Modifier.clickable(onClick = onLeading),
      )
    }
    Text(
      text = title,
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.alignByBaseline().widthIn(max = 200.dp),
    )
    Box(
      modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
      contentAlignment = Alignment.CenterEnd,
    ) { trailing?.invoke() }
  }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
  GlassIconButton(CommunityIcons.ChevronLeft, contentDescription = null, onClick = onBack)
}
