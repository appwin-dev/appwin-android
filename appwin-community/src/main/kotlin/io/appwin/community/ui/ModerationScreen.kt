package io.appwin.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.data.CommunityModerationRepository
import io.appwin.community.domain.ModerationAction
import io.appwin.community.domain.ModerationContentStatus
import io.appwin.community.domain.ModerationQueueItem
import io.appwin.community.domain.isHarmfulReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The moderation queue of moderators and admins (Figma 182:857). */
@Stable
private class ModerationQueue(
  private val repository: CommunityModerationRepository,
  private val scope: CoroutineScope,
) {
  var items by mutableStateOf<List<ModerationQueueItem>>(emptyList())
  var pendingCount by mutableStateOf(0)
  var loading by mutableStateOf(false)
  var loadFailed by mutableStateOf(false)
  var busyId by mutableStateOf<String?>(null)
  var failedId by mutableStateOf<String?>(null)
  private var hasMore = false

  fun load() = scope.launch {
    loading = true
    runCatching { repository.queue(offset = 0, limit = PAGE) }
      .onSuccess {
        items = it.items
        pendingCount = it.pendingCount
        hasMore = it.hasMore
        loadFailed = false
      }
      .onFailure { loadFailed = items.isEmpty() }
    loading = false
  }

  fun loadMoreIfNeeded(item: ModerationQueueItem) {
    if (!hasMore || loading || item.id != items.lastOrNull()?.id) return
    scope.launch {
      loading = true
      runCatching { repository.queue(offset = items.size, limit = PAGE) }.onSuccess { page ->
        val known = items.map { it.id }.toSet()
        items = items + page.items.filterNot { it.id in known }
        pendingCount = page.pendingCount
        hasMore = page.hasMore
      }
      loading = false
    }
  }

  /** Settles an item: it leaves the queue once the server has it. */
  fun decide(item: ModerationQueueItem, action: ModerationAction) = scope.launch {
    busyId = item.id
    failedId = null
    runCatching {
      repository.decide(item.targetType, item.targetId, action, reportIds = item.reports.map { it.id })
    }
      .onSuccess {
        items = items.filterNot { it.id == item.id }
        pendingCount = (pendingCount - 1).coerceAtLeast(0)
      }
      .onFailure { failedId = item.id }
    busyId = null
  }

  private companion object {
    const val PAGE = 20
  }
}

/** Figma « Modération (N) » (182:857): the queue moderators and admins decide on. */
@Composable
internal fun ModerationScreen(viewModel: CommunityViewModel, strings: CommunityStrings, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val queue = remember { ModerationQueue(viewModel.moderation, scope) }
  var reportsOf by remember { mutableStateOf<ModerationQueueItem?>(null) }
  LaunchedEffect(Unit) { queue.load() }
  LaunchedEffect(queue.pendingCount) { if (!queue.loading) viewModel.setModerationPendingCount(queue.pendingCount) }

  Column(modifier = Modifier.fillMaxSize().background(CommunityColors.background)) {
    ScreenHeader(strings.moderationTitle(queue.pendingCount), onBack)
    when {
      queue.items.isEmpty() && queue.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      queue.items.isEmpty() -> CommunityEmptyState(
        icon = Icons.Default.Info,
        title = if (queue.loadFailed) strings.loadErrorTitle else strings.moderationEmpty,
        message = if (queue.loadFailed) strings.loadErrorMessage else strings.moderationEmptyHint,
        actionLabel = if (queue.loadFailed) strings.retry else null,
        onAction = if (queue.loadFailed) ({ queue.load() }) else null,
      )
      else -> LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        item { CommunityInfoBox(strings.moderationInfo) }
        items(queue.items, key = { it.id }) { item ->
          LaunchedEffect(item.id) { queue.loadMoreIfNeeded(item) }
          QueueCard(
            item = item,
            viewModel = viewModel,
            strings = strings,
            busy = queue.busyId == item.id,
            failed = queue.failedId == item.id,
            onDecide = { queue.decide(item, it) },
            onShowReports = { reportsOf = item },
          )
        }
      }
    }
  }

  reportsOf?.let { item ->
    ReportsListSheet(item.reports, item.status == ModerationContentStatus.PENDING, strings) { reportsOf = null }
  }
}

/** Figma header of a pushed screen (182:857): a round back button and the title centred. */
@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.align(Alignment.CenterStart)) { BackButton(onBack) }
    Text(
      title,
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium,
      color = CommunityColors.textPrimary,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 56.dp),
    )
  }
}

/**
 * One item of the queue (Figma 182:857): why it is there, the content as
 * members see it with its sanction, and the two decisions.
 */
@Composable
private fun QueueCard(
  item: ModerationQueueItem,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  busy: Boolean,
  failed: Boolean,
  onDecide: (ModerationAction) -> Unit,
  onShowReports: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val sanction = when (item.status) {
    ModerationContentStatus.REMOVED -> SanctionKind.REMOVED
    ModerationContentStatus.PENDING -> SanctionKind.HIDDEN
    else -> null
  }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Figma « motif »: a tab overlapping the card's top edge.
    Column {
      CauseTab(item, strings, onShowReports, Modifier.offset(y = 8.dp).zIndex(1f))
      val comment = item.comment
      if (comment != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(state.config.radiusCard))
            .background(CommunityColors.surface)
            .padding(16.dp),
        ) {
          CommentRow(
            comment = comment,
            strings = strings,
            canReact = false,
            canReply = false,
            availableReactions = state.config.features.reactions,
            onOpenProfile = {},
            onReact = { _, _ -> },
            onReply = {},
            indent = 0,
            canOpenProfile = false,
          )
          sanction?.let { Box(Modifier.align(Alignment.TopEnd)) { SanctionBadge(it, strings) } }
        }
      } else {
        PostCard(
          post = item.post,
          config = state.config,
          strings = strings,
          onOpen = {},
          onReact = {},
          onOpenProfile = {},
          sanction = sanction,
        )
      }
    }
    DecisionButtons(item.status, strings, busy, onDecide)
    if (failed) CommunityErrorLine(strings)
  }
}

/** The classifier's category first: it acted alone, members' reports did not. */
@Composable
private fun CauseTab(item: ModerationQueueItem, strings: CommunityStrings, onShowReports: () -> Unit, modifier: Modifier) {
  val category = item.aiCategories.firstOrNull()
  val (icon, label, color) = when {
    category != null -> Triple(
      SolarModerationIcons.DangerTriangle,
      strings.reasonLabel(category),
      if (isHarmfulReason(category)) CommunityColors.alert else CommunityColors.caution,
    )
    item.reports.isNotEmpty() -> Triple(
      SolarModerationIcons.Flag,
      if (item.reports.size == 1) strings.reportsCountOne(1) else strings.reportsCount(item.reports.size),
      CommunityColors.caution,
    )
    else -> return
  }
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(CommunityColors.surface)
      .border(3.dp, CommunityColors.background, RoundedCornerShape(8.dp))
      .then(if (category == null) Modifier.clickable(onClick = onShowReports) else Modifier)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
  }
}

/**
 * Same pairs as the dashboard queue: a sanction already in place is confirmed
 * or put back; live content is left or hidden.
 */
@Composable
private fun DecisionButtons(
  status: ModerationContentStatus,
  strings: CommunityStrings,
  busy: Boolean,
  onDecide: (ModerationAction) -> Unit,
) {
  val sanctioned = status == ModerationContentStatus.REMOVED || status == ModerationContentStatus.PENDING
  val removes = status == ModerationContentStatus.REMOVED
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    DecisionButton(
      title = if (sanctioned) strings.restore else strings.leave,
      icon = if (sanctioned) SolarModerationIcons.UndoLeftRound else SolarModerationIcons.Check,
      iconColor = CommunityColors.textTertiary,
      foreground = CommunityColors.textPrimary,
      fill = CommunityColors.surface,
      bordered = true,
      enabled = !busy,
      modifier = Modifier.weight(1f),
    ) { onDecide(if (sanctioned) ModerationAction.RESTORE_CONTENT else ModerationAction.APPROVE) }
    DecisionButton(
      title = when (status) {
        ModerationContentStatus.REMOVED -> strings.confirmRemove
        ModerationContentStatus.PENDING -> strings.confirmHide
        else -> strings.hide
      },
      icon = if (removes) SolarModerationIcons.Forbidden else SolarModerationIcons.Ghost,
      iconColor = Color.White,
      foreground = Color.White,
      fill = if (removes) CommunityColors.alert else CommunityColors.caution,
      bordered = false,
      enabled = !busy,
      modifier = Modifier.weight(1f),
    ) { onDecide(if (removes) ModerationAction.REMOVE_CONTENT else ModerationAction.HIDE_CONTENT) }
  }
}

@Composable
private fun DecisionButton(
  title: String,
  icon: ImageVector,
  iconColor: Color,
  foreground: Color,
  fill: Color,
  bordered: Boolean,
  enabled: Boolean,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier = modifier
      .clip(shape)
      .background(fill)
      .then(if (bordered) Modifier.border(1.dp, CommunityColors.border, shape) else Modifier)
      .clickable(enabled = enabled, onClick = onClick)
      .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
  ) {
    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(12.dp))
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = foreground, maxLines = 1)
  }
}
