package io.appwin.community.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.appwin.community.data.ApiCommunityRepository
import io.appwin.community.data.CommunityRepository
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityConfig
import io.appwin.community.domain.CommunityFeedSort
import io.appwin.community.domain.CommunityGroup
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityPoll
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.CommunityReactionResult
import io.appwin.community.domain.CommunityReportReason
import io.appwin.community.domain.optimisticReactionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the feed screen needs to know, in one object. */
internal data class CommunityUiState(
  val isLoading: Boolean = true,
  val isRefreshing: Boolean = false,
  val isLoadingMore: Boolean = false,
  val loadFailed: Boolean = false,
  val config: CommunityConfig = CommunityConfig(),
  val groups: List<CommunityGroup> = emptyList(),
  val profile: CommunityProfile? = null,
  val selectedGroupId: String? = null,
  val posts: List<CommunityPost> = emptyList(),
  val nextCursor: String? = null,
  val unreadNotificationCount: Int = 0,
) {
  val canPostHere: Boolean
    get() {
      if (!config.features.postsEnabled) return false
      if (profile?.isBanned == true) return false
      val group = groups.firstOrNull { it.id == selectedGroupId }
      return group?.canPost ?: groups.any { it.canPost }
    }
}

/**
 * Feed state.
 *
 * One `ViewModel` for the feed rather than one per screen: the configuration,
 * the groups and the profile drive the display everywhere, and loading them
 * separately in each screen would produce that many offset loading states. Same
 * reasoning as the shared context on the dashboard side.
 */
internal class CommunityViewModel(
  private val repository: CommunityRepository = ApiCommunityRepository(),
) : ViewModel() {

  private val _state = MutableStateFlow(CommunityUiState())
  val state: StateFlow<CommunityUiState> = _state.asStateFlow()

  private val _events = MutableStateFlow<String?>(null)
  val events: StateFlow<String?> = _events.asStateFlow()

  /** Seen posts, so each view is sent once per session. */
  private val trackedViews = mutableSetOf<String>()

  init {
    load()
    viewModelScope.launch {
      CommunityUiRefresh.requests.collect {
        // Soft refresh: keep the list on screen while identity / profile update.
        refreshFeed()
      }
    }
  }

  fun load() {
    viewModelScope.launch {
      _state.update { it.copy(isLoading = true, loadFailed = false) }
      runCatching { repository.bootstrap() }
        .onSuccess { boot ->
          _state.update {
            it.copy(
              isLoading = false,
              config = boot.config,
              groups = boot.groups,
              profile = boot.profile,
              unreadNotificationCount = boot.unreadNotificationCount,
            )
          }
          if (boot.config.features.enabled) {
            runCatching {
              repository.feed(
                groupId = _state.value.selectedGroupId,
                sort = CommunityFeedSort.RECENT,
              )
            }
              .onSuccess { page ->
                _state.update {
                  it.copy(posts = page.items, nextCursor = page.nextCursor)
                }
              }
              .onFailure { _state.update { it.copy(loadFailed = true) } }
          }
        }
        .onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
    }
  }

  fun selectGroup(groupId: String?) {
    if (groupId == _state.value.selectedGroupId) return
    _state.update { it.copy(selectedGroupId = groupId, posts = emptyList(), nextCursor = null) }
    refreshFeed()
  }

  fun refreshFeed() {
    viewModelScope.launch {
      _state.update { it.copy(isRefreshing = true, loadFailed = false) }
      // Soft config refresh like iOS pull-to-refresh, without clearing the list.
      runCatching { repository.bootstrap() }
        .onSuccess { boot ->
          _state.update {
            it.copy(
              config = boot.config,
              groups = boot.groups,
              profile = boot.profile,
              unreadNotificationCount = boot.unreadNotificationCount,
            )
          }
        }
      runCatching {
        repository.feed(groupId = _state.value.selectedGroupId, sort = CommunityFeedSort.RECENT)
      }
        .onSuccess { page ->
          _state.update {
            it.copy(isRefreshing = false, posts = page.items, nextCursor = page.nextCursor)
          }
        }
        .onFailure { _state.update { it.copy(isRefreshing = false, loadFailed = true) } }
    }
  }

  /**
   * Page suivante.
   *
   * `nextCursor == null` is the **only** stop condition: a page shorter than
   * the limit does not mean the end, since the server can filter after
   * paginating.
   */
  fun loadMore() {
    val current = _state.value
    val cursor = current.nextCursor ?: return
    if (current.isLoadingMore) return

    viewModelScope.launch {
      _state.update { it.copy(isLoadingMore = true) }
      runCatching {
        repository.feed(groupId = current.selectedGroupId, cursor = cursor)
      }
        .onSuccess { page ->
          _state.update {
            val known = it.posts.map { post -> post.id }.toSet()
            val appended = page.items.filter { post -> post.id !in known }
            it.copy(
              isLoadingMore = false,
              posts = it.posts + appended,
              nextCursor = page.nextCursor,
            )
          }
        }
        .onFailure { _state.update { it.copy(isLoadingMore = false) } }
    }
  }

  /**
   * Reaction, applied **optimistically**.
   *
   * The server returns the updated counter, but waiting for the round trip
   * would make the button feel dead. On failure the previous state is restored:
   * a heart that un-fills beats a counter that lies.
   */
  fun toggleReaction(post: CommunityPost, kind: CommunityReactionKind) {
    val previous = post.myReaction
    val optimistic = optimisticReactionState(
      myReaction = post.myReaction,
      reactionCounts = post.reactionCounts,
      likeCount = post.likeCount,
      kind = kind,
    )
    patchPost(post.id) {
      it.copy(
        myReaction = optimistic.myReaction,
        topReactions = optimistic.topReactions,
        reactionCounts = optimistic.reactionCounts,
        likeCount = optimistic.likeCount,
      )
    }

    viewModelScope.launch {
      runCatching { repository.reactToPost(post.id, kind) }
        .onSuccess { result ->
          patchPost(post.id) {
            it.copy(
              myReaction = result.myReaction,
              topReactions = result.topReactions,
              reactionCounts = result.reactionCounts,
              likeCount = result.likeCount,
            )
          }
        }
        .onFailure {
          patchPost(post.id) {
            it.copy(
              myReaction = previous,
              topReactions = post.topReactions,
              reactionCounts = post.reactionCounts,
              likeCount = post.likeCount,
            )
          }
        }
    }
  }

  /** Reports displayed posts. Each post is counted once. */
  fun trackVisible(postIds: List<String>) {
    if (!_state.value.config.features.viewsEnabled) return
    // Lazy keys can include chrome ids ("empty"); Zod rejects the whole batch
    // if any element is not a UUID, and we must not mark those as sent.
    val uuidRegex =
      Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    val fresh = postIds.filter { it.matches(uuidRegex) && it !in trackedViews }
    if (fresh.isEmpty()) return
    trackedViews += fresh
    viewModelScope.launch {
      runCatching { repository.trackViews(fresh) }
        .onFailure { trackedViews.removeAll(fresh.toSet()) }
    }
  }

  fun createPost(
    body: String,
    groupId: String? = _state.value.selectedGroupId,
    media: List<CommunityMedia> = emptyList(),
    pollOptions: List<String>? = null,
    onDone: (Boolean) -> Unit,
  ) {
    viewModelScope.launch {
      runCatching { repository.createPost(groupId, body, media, pollOptions) }
        .onSuccess { post ->
          _state.update { it.copy(posts = insertInFeedOrder(it.posts, post)) }
          onDone(true)
        }
        .onFailure { onDone(false) }
    }
  }

  fun updatePost(
    postId: String,
    body: String,
    groupId: String?,
    media: List<CommunityMedia>,
    onDone: (Boolean) -> Unit,
  ) {
    viewModelScope.launch {
      runCatching { repository.updatePost(postId, body, groupId, media) }
        .onSuccess { updated ->
          _state.update { s ->
            val selected = s.selectedGroupId
            val posts = if (selected != null && updated.groupId != selected) {
              s.posts.filterNot { it.id == updated.id }
            } else {
              s.posts.map { if (it.id == updated.id) updated else it }
            }
            s.copy(posts = posts)
          }
          onDone(true)
        }
        .onFailure { onDone(false) }
    }
  }

  fun voteOnPoll(post: CommunityPost, optionId: String) {
    viewModelScope.launch {
      runCatching { repository.voteOnPoll(post.id, optionId) }
        .onSuccess { poll -> patchPost(post.id) { it.copy(poll = poll) } }
    }
  }

  suspend fun uploadMedia(
    data: ByteArray,
    mimeType: String,
    filename: String,
  ) = repository.uploadMedia(data, mimeType, filename)

  suspend fun updateOwnProfile(
    nickname: String?,
    bio: String?,
    avatarUrl: String?,
    isAnonymous: Boolean?,
  ) = repository.updateOwnProfile(nickname, bio, avatarUrl, isAnonymous).also { updated ->
    _state.update { it.copy(profile = updated) }
  }

  suspend fun authorPosts(authorProfileId: String, cursor: String? = null) =
    repository.feed(authorProfileId = authorProfileId, cursor = cursor)

  fun deletePost(postId: String) {
    viewModelScope.launch {
      runCatching { repository.deletePost(postId) }
        .onSuccess { _state.update { s -> s.copy(posts = s.posts.filterNot { it.id == postId }) } }
    }
  }

  fun report(
    targetType: String,
    targetId: String,
    reason: CommunityReportReason,
    onDone: () -> Unit,
  ) {
    viewModelScope.launch {
      runCatching { repository.report(targetType, targetId, reason) }
      onDone()
    }
  }

  suspend fun comments(postId: String): Result<List<CommunityComment>> =
    runCatching { repository.comments(postId) }

  suspend fun createComment(
    postId: String,
    body: String,
    parentCommentId: String?,
    media: List<CommunityMedia> = emptyList(),
  ): Result<CommunityComment> =
    runCatching { repository.createComment(postId, body, parentCommentId, media) }
      .onSuccess { patchPost(postId) { it.copy(commentCount = it.commentCount + 1) } }

  suspend fun reactToComment(
    commentId: String,
    kind: CommunityReactionKind,
  ): Result<CommunityReactionResult> =
    runCatching { repository.reactToComment(commentId, kind) }

  suspend fun profile(profileId: String): Result<CommunityProfile> =
    runCatching { repository.profile(profileId) }

  fun consumeEvent() {
    _events.value = null
  }

  fun emit(message: String) {
    _events.value = message
  }

  private fun patchPost(postId: String, transform: (CommunityPost) -> CommunityPost) {
    _state.update { state ->
      state.copy(posts = state.posts.map { if (it.id == postId) transform(it) else it })
    }
  }
}

/**
 * Same order as the API feed: pinned first, then newest. A freshly created
 * non-pinned post slots under the pinned block instead of jumping above it.
 */
private fun insertInFeedOrder(
  posts: List<CommunityPost>,
  post: CommunityPost,
): List<CommunityPost> {
  if (post.isPinned) return listOf(post) + posts.filterNot { it.id == post.id }
  val without = posts.filterNot { it.id == post.id }
  val insertAt = without.indexOfFirst { !it.isPinned }.let { if (it < 0) without.size else it }
  return without.take(insertAt) + post + without.drop(insertAt)
}
