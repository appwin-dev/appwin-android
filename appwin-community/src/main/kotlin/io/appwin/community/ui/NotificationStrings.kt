package io.appwin.community.ui

import io.appwin.community.R
import io.appwin.community.domain.CommunityNotificationType

// Labels of the bell's inbox.

internal val CommunityStrings.notificationsTitle: String
  get() = t("appwin_community_notifications_title", R.string.appwin_community_notifications_title)

internal val CommunityStrings.notificationsEmpty: String
  get() = t("appwin_community_notifications_empty", R.string.appwin_community_notifications_empty)

internal val CommunityStrings.notificationsEmptyHint: String
  get() = t("appwin_community_notifications_empty_hint", R.string.appwin_community_notifications_empty_hint)

internal val CommunityStrings.activitySomeone: String
  get() = t("appwin_community_activity_someone", R.string.appwin_community_activity_someone)

/** « 1 other » / « 3 others », after the first actor's name. */
internal fun CommunityStrings.activityOthers(count: Int): String =
  if (count == 1) {
    t("appwin_community_activity_others_one", R.string.appwin_community_activity_others_one)
  } else {
    tf("appwin_community_activity_others_many", R.string.appwin_community_activity_others_many, count)
  }

/** Sentence of an inbox entry; [others] is set when the entry groups several members. */
internal fun CommunityStrings.activitySentence(
  type: CommunityNotificationType,
  actor: String,
  others: String?,
): String {
  val (key, resId) = when (type) {
    CommunityNotificationType.POST_COMMENT ->
      "appwin_community_activity_commented" to R.string.appwin_community_activity_commented
    CommunityNotificationType.COMMENT_REPLY ->
      "appwin_community_activity_replied" to R.string.appwin_community_activity_replied
    CommunityNotificationType.ADMIN_POST ->
      "appwin_community_activity_admin_post" to R.string.appwin_community_activity_admin_post
    CommunityNotificationType.POST_REACTION -> if (others == null) {
      "appwin_community_activity_liked_post" to R.string.appwin_community_activity_liked_post
    } else {
      "appwin_community_activity_liked_post_group" to R.string.appwin_community_activity_liked_post_group
    }
    CommunityNotificationType.COMMENT_REACTION -> if (others == null) {
      "appwin_community_activity_liked_comment" to R.string.appwin_community_activity_liked_comment
    } else {
      "appwin_community_activity_liked_comment_group" to R.string.appwin_community_activity_liked_comment_group
    }
    CommunityNotificationType.POLL_VOTE -> if (others == null) {
      "appwin_community_activity_voted" to R.string.appwin_community_activity_voted
    } else {
      "appwin_community_activity_voted_group" to R.string.appwin_community_activity_voted_group
    }
    else -> return ""
  }
  return if (others == null) tf(key, resId, actor) else tf(key, resId, actor, others)
}
