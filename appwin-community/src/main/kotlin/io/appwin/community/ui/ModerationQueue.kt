package io.appwin.community.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.appwin.community.data.CommunityModerationRepository
import io.appwin.community.domain.ModerationAction
import io.appwin.community.domain.ModerationQueueItem
import io.appwin.community.domain.ModerationQueueTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The moderation queue of moderators and admins (Figma 182:857), in two tabs
 * as on the dashboard: « À traiter » and « Traités ». Mirror of the Swift
 * `ModerationStore`.
 */
@Stable
internal class ModerationQueue(
  private val repository: CommunityModerationRepository,
  private val scope: CoroutineScope,
) {
  var tab by mutableStateOf(ModerationQueueTab.PENDING)
    private set
  var items by mutableStateOf<List<ModerationQueueItem>>(emptyList())
  var pendingCount by mutableStateOf(0)
  var loading by mutableStateOf(false)
  var loadFailed by mutableStateOf(false)
  var busyId by mutableStateOf<String?>(null)
  var failedId by mutableStateOf<String?>(null)
  private var hasMore = false
  private var loadJob: Job? = null

  fun select(tab: ModerationQueueTab) {
    if (tab == this.tab) return
    this.tab = tab
    items = emptyList()
    hasMore = false
    failedId = null
    load()
  }

  fun load() {
    // A tab switched while loading: the other tab's page must not land here.
    loadJob?.cancel()
    val tab = tab
    loadJob = scope.launch {
      loading = true
      runCatching { repository.queue(tab, offset = 0, limit = PAGE) }
        .onSuccess {
          items = it.items
          pendingCount = it.pendingCount
          hasMore = it.hasMore
          loadFailed = false
        }
        .onFailure { loadFailed = items.isEmpty() }
      loading = false
    }
  }

  fun loadMoreIfNeeded(item: ModerationQueueItem) {
    if (!hasMore || loading || item.id != items.lastOrNull()?.id) return
    val tab = tab
    loadJob = scope.launch {
      loading = true
      runCatching { repository.queue(tab, offset = items.size, limit = PAGE) }.onSuccess { page ->
        val known = items.map { it.id }.toSet()
        items = items + page.items.filterNot { it.id in known }
        pendingCount = page.pendingCount
        hasMore = page.hasMore
      }
      loading = false
    }
  }

  /**
   * « À traiter »: the item leaves the queue once the server has it.
   * « Traités »: it stays, with its new state and this moderator's name.
   */
  fun decide(item: ModerationQueueItem, action: ModerationAction, actorName: String) = scope.launch {
    val tab = tab
    busyId = item.id
    failedId = null
    runCatching {
      repository.decide(
        item.targetType,
        item.targetId,
        action,
        // Decided reports are closed already: only open ones are settled.
        reportIds = if (tab == ModerationQueueTab.PENDING) item.reports.map { it.id } else emptyList(),
      )
    }
      .onSuccess {
        if (tab != this@ModerationQueue.tab) return@onSuccess
        when (tab) {
          ModerationQueueTab.PENDING -> {
            items = items.filterNot { it.id == item.id }
            pendingCount = (pendingCount - 1).coerceAtLeast(0)
          }
          ModerationQueueTab.RESOLVED -> items = items.map {
            if (it.id == item.id) it.decided(action, actorName, System.currentTimeMillis()) else it
          }
        }
      }
      .onFailure { failedId = item.id }
    busyId = null
  }

  private companion object {
    const val PAGE = 20
  }
}
