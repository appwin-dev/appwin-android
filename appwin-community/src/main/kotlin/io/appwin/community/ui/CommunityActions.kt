package io.appwin.community.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.appwin.community.domain.CommunityAuthor
import io.appwin.community.domain.CommunityComment
import io.appwin.community.domain.CommunityMemberRole
import io.appwin.community.domain.CommunityPost
import io.appwin.community.domain.CommunityProfile
import io.appwin.community.domain.ModerationAction
import io.appwin.community.domain.ModerationReason
import io.appwin.community.domain.ModerationTargetType
import kotlinx.coroutines.launch

/** What the « ⋯ » menu acts on. */
internal sealed interface ActionTarget {
  data class Post(val post: CommunityPost) : ActionTarget
  data class Comment(val comment: CommunityComment) : ActionTarget
  data class Member(val author: CommunityAuthor) : ActionTarget
}

/** How the screen showing the content follows up on an action. */
internal data class ActionHandlers(
  val onEdit: ((CommunityPost) -> Unit)? = null,
  /** Deleted by its author, hidden or removed by a moderator: gone from this screen. */
  val onPostGone: (String) -> Unit = {},
  val onCommentGone: (CommunityComment) -> Unit = {},
)

internal sealed interface ActionSheet {
  data class Report(val type: String, val id: String) : ActionSheet
  data class Reason(val kind: ReasonSheetKind, val target: ActionTarget) : ActionSheet
  data class Group(val post: CommunityPost) : ActionSheet
  data class Pin(val post: CommunityPost) : ActionSheet
  data class Sanction(val kind: MemberSanctionKind, val member: CommunityAuthor) : ActionSheet
}

/** One per screen: the [ActionsMenu]s of its posts, comments and profile open sheets through it. */
@Stable
internal class CommunityActions {
  var sheet: ActionSheet? by mutableStateOf(null)
  var pendingDeletion: ActionTarget? by mutableStateOf(null)
  var handlers = ActionHandlers()

  /** Runs an action that needs no sheet (unpin) in the host's scope. */
  var launch: (suspend () -> Unit) -> Unit = {}
}

private val LocalCommunityActions = staticCompositionLocalOf<CommunityActions?> { null }

/**
 * Gives the screen's [ActionsMenu]s their controller, runs what they ask, and
 * presents the sheets and the delete confirmation.
 */
@Composable
internal fun CommunityActionsHost(
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  handlers: ActionHandlers,
  content: @Composable () -> Unit,
) {
  val actions = remember { CommunityActions() }
  actions.handlers = handlers
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  actions.launch = { block -> scope.launch { runCatching { block() } } }

  CompositionLocalProvider(LocalCommunityActions provides actions, content = content)

  actions.pendingDeletion?.let { target ->
    // An alert, centred: never a sheet that opens far from the « ⋯ » it came from.
    AlertDialog(
      onDismissRequest = { actions.pendingDeletion = null },
      title = { Text(if (target is ActionTarget.Comment) strings.deleteCommentTitle else strings.deletePostTitle) },
      text = if (target is ActionTarget.Post) ({ Text(strings.deletePostMessage) }) else null,
      confirmButton = {
        TextButton(onClick = {
          actions.pendingDeletion = null
          when (target) {
            is ActionTarget.Post -> {
              viewModel.deletePost(target.post.id)
              handlers.onPostGone(target.post.id)
            }
            is ActionTarget.Comment -> {
              // Optimistic: a comment that stays after Delete is more confusing than one that comes back.
              handlers.onCommentGone(target.comment)
              scope.launch { runCatching { viewModel.deleteComment(target.comment.id) } }
            }
            is ActionTarget.Member -> Unit
          }
        }) { Text(strings.delete, color = CommunityColors.alert) }
      },
      dismissButton = { TextButton(onClick = { actions.pendingDeletion = null }) { Text(strings.cancel) } },
    )
  }

  val close = { actions.sheet = null }
  when (val sheet = actions.sheet) {
    is ActionSheet.Report -> ReportSheet(strings, close) { reason ->
      viewModel.report(sheet.type, sheet.id, reason)
    }
    is ActionSheet.Reason -> ModerationReasonSheet(sheet.kind, authorName(sheet.target), strings, close) { reason ->
      val action = if (sheet.kind == ReasonSheetKind.REMOVE) ModerationAction.REMOVE_CONTENT else ModerationAction.HIDE_CONTENT
      decide(viewModel, actions.handlers, sheet.target, action, reason)
    }
    is ActionSheet.Group -> GroupPickerSheet(state.groups, sheet.post.groupId, strings, close) { groupId ->
      viewModel.moderation.moveToGroup(sheet.post.id, groupId)
      viewModel.refreshPost(sheet.post.id)
    }
    is ActionSheet.Pin -> PinSettingsSheet(strings, close) { settings ->
      viewModel.moderation.pin(sheet.post.id, settings)
      viewModel.refreshPost(sheet.post.id)
    }
    is ActionSheet.Sanction -> MemberSanctionSheet(sheet.kind, sheet.member, strings, close) { text, hours ->
      val action = when (sheet.kind) {
        MemberSanctionKind.WARN -> ModerationAction.WARN
        MemberSanctionKind.SHADOW_BAN -> ModerationAction.SHADOW_BAN
        MemberSanctionKind.BAN -> ModerationAction.BAN
      }
      viewModel.moderation.decide(ModerationTargetType.PROFILE, sheet.member.id, action, text, hours)
    }
    null -> Unit
  }
}

private suspend fun decide(
  viewModel: CommunityViewModel,
  handlers: ActionHandlers,
  target: ActionTarget,
  action: ModerationAction,
  reason: ModerationReason,
) {
  when (target) {
    is ActionTarget.Post -> {
      viewModel.moderation.decide(ModerationTargetType.POST, target.post.id, action, reason.wire)
      viewModel.removePostLocally(target.post.id)
      handlers.onPostGone(target.post.id)
    }
    is ActionTarget.Comment -> {
      viewModel.moderation.decide(ModerationTargetType.COMMENT, target.comment.id, action, reason.wire)
      handlers.onCommentGone(target.comment)
    }
    is ActionTarget.Member -> Unit
  }
}

private fun authorName(target: ActionTarget): String = when (target) {
  is ActionTarget.Post -> target.post.author?.nickname.orEmpty()
  is ActionTarget.Comment -> target.comment.author?.nickname.orEmpty()
  is ActionTarget.Member -> target.author.nickname
}

/**
 * The « ⋯ » of a post, a comment or a profile (Figma 178:2735), its menu
 * opening right on the dots: members edit, delete or report; moderators and
 * admins also move, pin, hide, delete with a motif, and sanction members.
 *
 * [modifier] sizes the tap target, larger than the glyph: a near miss used
 * to open the post underneath. Needs a [CommunityActionsHost] on the screen.
 */
@Composable
internal fun ActionsMenu(
  target: ActionTarget,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  modifier: Modifier = Modifier.size(44.dp),
  glyphSize: Dp = 16.dp,
  contentAlignment: Alignment = Alignment.Center,
  glyphPadding: PaddingValues = PaddingValues(0.dp),
) {
  val actions = LocalCommunityActions.current ?: return
  val state by viewModel.state.collectAsStateWithLifecycle()
  val items = menuItems(target, state, viewModel, actions, strings)
  if (items.isEmpty()) return
  var open by remember { mutableStateOf(false) }
  Box(
    modifier = modifier.clickable { open = true },
    contentAlignment = contentAlignment,
  ) {
    Icon(
      CommunityIcons.Ellipsis,
      contentDescription = null,
      tint = CommunityColors.textTertiary,
      modifier = Modifier.padding(glyphPadding).size(glyphSize),
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      items.forEach { item ->
        DropdownMenuItem(
          text = { Text(item.label, color = if (item.destructive) CommunityColors.alert else CommunityColors.textPrimary) },
          onClick = {
            open = false
            item.action()
          },
        )
      }
    }
  }
}

private class MenuItem(val label: String, val destructive: Boolean = false, val action: () -> Unit)

private fun menuItems(
  target: ActionTarget,
  state: CommunityUiState,
  viewModel: CommunityViewModel,
  actions: CommunityActions,
  strings: CommunityStrings,
): List<MenuItem> = buildList {
  val me = state.profile
  val reportingEnabled = state.config.features.reportingEnabled
  when (target) {
    is ActionTarget.Post -> {
      val post = target.post
      val moderates = canModerate(me, post.author)
      val onEdit = actions.handlers.onEdit
      if (post.canEdit && onEdit != null) add(MenuItem(strings.edit) { onEdit(post) })
      if (me?.canModerate == true) {
        if (state.groups.size > 1) add(MenuItem(strings.moveToGroup) { actions.sheet = ActionSheet.Group(post) })
        if (post.isPinned) {
          add(
            MenuItem(strings.unpinAction) {
              actions.launch {
                viewModel.moderation.unpin(post.id)
                viewModel.refreshPost(post.id)
              }
            },
          )
        } else {
          add(MenuItem(strings.pinAction) { actions.sheet = ActionSheet.Pin(post) })
        }
      }
      if (moderates) {
        add(MenuItem(strings.hide) { actions.sheet = ActionSheet.Reason(ReasonSheetKind.HIDE, target) })
        add(MenuItem(strings.delete, destructive = true) { actions.sheet = ActionSheet.Reason(ReasonSheetKind.REMOVE, target) })
      } else if (post.canDelete) {
        add(MenuItem(strings.delete, destructive = true) { actions.pendingDeletion = target })
      }
      if (!post.canEdit && !moderates && reportingEnabled) {
        add(MenuItem(strings.report) { actions.sheet = ActionSheet.Report("post", post.id) })
      }
    }
    is ActionTarget.Comment -> {
      val comment = target.comment
      val moderates = canModerate(me, comment.author)
      if (moderates) {
        add(MenuItem(strings.hide) { actions.sheet = ActionSheet.Reason(ReasonSheetKind.HIDE, target) })
        add(MenuItem(strings.delete, destructive = true) { actions.sheet = ActionSheet.Reason(ReasonSheetKind.REMOVE, target) })
      } else if (comment.canDelete) {
        add(MenuItem(strings.delete, destructive = true) { actions.pendingDeletion = target })
      }
      if (!comment.canEdit && !moderates && reportingEnabled) {
        add(MenuItem(strings.report) { actions.sheet = ActionSheet.Report("comment", comment.id) })
      }
    }
    is ActionTarget.Member -> {
      val author = target.author
      if (canModerate(me, author)) {
        add(MenuItem(strings.warnAction) { actions.sheet = ActionSheet.Sanction(MemberSanctionKind.WARN, author) })
        add(MenuItem(strings.shadowBanAction) { actions.sheet = ActionSheet.Sanction(MemberSanctionKind.SHADOW_BAN, author) })
        add(MenuItem(strings.banAction, destructive = true) { actions.sheet = ActionSheet.Sanction(MemberSanctionKind.BAN, author) })
      } else if (reportingEnabled) {
        add(MenuItem(strings.report) { actions.sheet = ActionSheet.Report("profile", author.id) })
      }
    }
  }
}

/** Same rule as the API: never oneself nor an admin; a moderator only by an admin. */
private fun canModerate(me: CommunityProfile?, author: CommunityAuthor?): Boolean {
  if (me == null || !me.canModerate || author == null || !author.isAddressable || author.id == me.id) return false
  return when (author.role) {
    CommunityMemberRole.MEMBER -> true
    CommunityMemberRole.MODERATOR -> me.role == CommunityMemberRole.ADMIN
    CommunityMemberRole.ADMIN -> false
  }
}

/** A post's « ⋯ » over its card's corner, padding included (see [PostCard]'s `actionsMenu`). */
@Composable
internal fun PostActionsMenu(post: CommunityPost, viewModel: CommunityViewModel, strings: CommunityStrings, modifier: Modifier) =
  ActionsMenu(
    target = ActionTarget.Post(post),
    viewModel = viewModel,
    strings = strings,
    modifier = modifier,
    contentAlignment = Alignment.TopEnd,
    glyphPadding = PaddingValues(top = if (post.isPinned) 36.dp else 20.dp, end = 16.dp),
  )

/** A comment's « ⋯ », in its author line. */
@Composable
internal fun CommentActionsMenu(comment: CommunityComment, viewModel: CommunityViewModel, strings: CommunityStrings) =
  ActionsMenu(
    target = ActionTarget.Comment(comment),
    viewModel = viewModel,
    strings = strings,
    modifier = Modifier.size(width = 44.dp, height = 24.dp),
    glyphSize = 14.dp,
    contentAlignment = Alignment.CenterEnd,
  )
