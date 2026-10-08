package io.appwin.community.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.domain.CommunityAuthor
import io.appwin.community.domain.CommunityMemberRole
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityProfile

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

  val handlers = ActionHandlers(onEdit = onEditPost, onPostGone = { id -> posts = posts.filterNot { it.id == id } })
  ReactionFocusHost {
    CommunityActionsHost(viewModel, strings, handlers) {
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
                modifier = Modifier.fillMaxSize().reactionDimBehind(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                item {
                  Box(Modifier.reactionDimmed()) {
                  ProfileHeader(
                    member = member,
                    strings = strings,
                    onBack = onBack,
                    onEdit = { openProfileEditor { editing = true } },
                    // Others: reporting, or sanctions for moderators.
                    othersMenu = {
                      ActionsMenu(
                        target = ActionTarget.Member(
                          CommunityAuthor(member.id, member.nickname, member.avatarUrl, member.role, member.isTeam),
                        ),
                        viewModel = viewModel,
                        strings = strings,
                        glyphSize = 24.dp,
                      )
                    },
                  )
                  }
                }

                if (member.isBanned) {
                  item {
                    NoticeBanner(strings.bannedNotice, Modifier.padding(horizontal = 20.dp), danger = true)
                  }
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
                        onVote = { optionId -> viewModel.voteOnPoll(post, optionId) },
                        actionsMenu = { slot -> PostActionsMenu(post, viewModel, strings, slot) },
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
    }
  }
}

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
  othersMenu: @Composable () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  val glow = accent.copy(alpha = 0.24f)
  var showAvatarViewer by remember(member.id) { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 12.dp)
      .shadow(
        elevation = 20.dp,
        shape = RectangleShape,
        ambientColor = Color.Black.copy(alpha = 0.08f),
        spotColor = Color.Black.copy(alpha = 0.08f),
      )
      .background(CommunityColors.background)
      .drawBehind {
        drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), center = Offset.Zero, radius = 280.dp.toPx()))
      }
      .statusBarsPadding()
      .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      BackButton(onBack)
      Spacer(modifier = Modifier.weight(1f))
      if (member.isMe) {
        Box(
          modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = strings.editProfile, onClick = onEdit),
          contentAlignment = Alignment.Center,
        ) {
          Icon(CommunityIcons.Ellipsis, contentDescription = null, tint = CommunityColors.textTertiary, modifier = Modifier.size(24.dp))
        }
      } else {
        othersMenu()
      }
    }
    Box {
      Box(
        modifier = Modifier
          .size(88.dp)
          .border(3.dp, accent, CircleShape)
          .clip(CircleShape)
          .clickable(enabled = !member.avatarUrl.isNullOrBlank() || member.isMe) {
            if (!member.avatarUrl.isNullOrBlank()) showAvatarViewer = true else onEdit()
          },
      ) {
        CommunityAvatar(member.avatarUrl, member.nickname, size = 88)
      }
      if (member.role == CommunityMemberRole.ADMIN || member.isTeam) {
        AdminBadge(avatarSize = 72, modifier = Modifier.align(Alignment.BottomEnd))
      }
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

/** Stat pill: accent at 24% (iOS), value over label. */
@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .widthIn(max = 144.dp)
      .clip(RoundedCornerShape(LocalCommunityConfig.current.radiusField))
      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
      .padding(vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(1.dp),
  ) {
    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CommunityColors.textPrimary)
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textSecondary)
  }
}
