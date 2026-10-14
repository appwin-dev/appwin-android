package io.appwin.community.ui

import io.appwin.community.R
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.CommunityReportReason
import io.appwin.community.domain.ModerationReason
import io.appwin.community.domain.SanctionDuration

// Labels of the in-app moderation, reactions and report reasons. Same names as
// the Swift `CommunityStrings+Moderation`.

internal val CommunityStrings.reportSheetTitle: String
  get() = t("appwin_community_report_sheet_title", R.string.appwin_community_report_sheet_title)

internal val CommunityStrings.reportQuestion: String
  get() = t("appwin_community_report_question", R.string.appwin_community_report_question)

internal val CommunityStrings.validate: String
  get() = t("appwin_community_validate", R.string.appwin_community_validate)

internal val CommunityStrings.moderationInfo: String
  get() = t("appwin_community_moderation_info", R.string.appwin_community_moderation_info)

internal val CommunityStrings.moderationEmpty: String
  get() = t("appwin_community_moderation_empty", R.string.appwin_community_moderation_empty)

internal val CommunityStrings.moderationEmptyHint: String
  get() = t("appwin_community_moderation_empty_hint", R.string.appwin_community_moderation_empty_hint)

internal val CommunityStrings.restore: String
  get() = t("appwin_community_restore", R.string.appwin_community_restore)

internal val CommunityStrings.leave: String
  get() = t("appwin_community_leave", R.string.appwin_community_leave)

internal val CommunityStrings.hide: String
  get() = t("appwin_community_hide", R.string.appwin_community_hide)

internal val CommunityStrings.confirmHide: String
  get() = t("appwin_community_confirm_hide", R.string.appwin_community_confirm_hide)

internal val CommunityStrings.confirmRemove: String
  get() = t("appwin_community_confirm_remove", R.string.appwin_community_confirm_remove)

internal val CommunityStrings.statusHidden: String
  get() = t("appwin_community_status_hidden", R.string.appwin_community_status_hidden)

internal val CommunityStrings.statusRemoved: String
  get() = t("appwin_community_status_removed", R.string.appwin_community_status_removed)

internal val CommunityStrings.moveToGroup: String
  get() = t("appwin_community_move_to_group", R.string.appwin_community_move_to_group)

internal val CommunityStrings.pinAction: String
  get() = t("appwin_community_pin_action", R.string.appwin_community_pin_action)

internal val CommunityStrings.unpinAction: String
  get() = t("appwin_community_unpin_action", R.string.appwin_community_unpin_action)

internal val CommunityStrings.chooseReason: String
  get() = t("appwin_community_choose_reason", R.string.appwin_community_choose_reason)

internal val CommunityStrings.chooseGroupTitle: String
  get() = t("appwin_community_choose_group_title", R.string.appwin_community_choose_group_title)

internal val CommunityStrings.moveAction: String
  get() = t("appwin_community_move_action", R.string.appwin_community_move_action)

internal val CommunityStrings.pinTitle: String
  get() = t("appwin_community_pin_title", R.string.appwin_community_pin_title)

internal val CommunityStrings.pinInfo: String
  get() = t("appwin_community_pin_info", R.string.appwin_community_pin_info)

internal val CommunityStrings.pinUntilTitle: String
  get() = t("appwin_community_pin_until_title", R.string.appwin_community_pin_until_title)

internal val CommunityStrings.pinUntilHint: String
  get() = t("appwin_community_pin_until_hint", R.string.appwin_community_pin_until_hint)

internal val CommunityStrings.pinViewsTitle: String
  get() = t("appwin_community_pin_views_title", R.string.appwin_community_pin_views_title)

internal val CommunityStrings.pinViewsHint: String
  get() = t("appwin_community_pin_views_hint", R.string.appwin_community_pin_views_hint)

internal val CommunityStrings.pinTimes: String
  get() = t("appwin_community_pin_times", R.string.appwin_community_pin_times)

internal val CommunityStrings.warnAction: String
  get() = t("appwin_community_warn_action", R.string.appwin_community_warn_action)

internal val CommunityStrings.shadowBanAction: String
  get() = t("appwin_community_shadow_ban_action", R.string.appwin_community_shadow_ban_action)

internal val CommunityStrings.banAction: String
  get() = t("appwin_community_ban_action", R.string.appwin_community_ban_action)

internal val CommunityStrings.warnTitle: String
  get() = t("appwin_community_warn_title", R.string.appwin_community_warn_title)

internal val CommunityStrings.message: String
  get() = t("appwin_community_message", R.string.appwin_community_message)

internal val CommunityStrings.shadowBanTitle: String
  get() = t("appwin_community_shadow_ban_title", R.string.appwin_community_shadow_ban_title)

internal val CommunityStrings.banTitle: String
  get() = t("appwin_community_ban_title", R.string.appwin_community_ban_title)

internal val CommunityStrings.chooseDuration: String
  get() = t("appwin_community_choose_duration", R.string.appwin_community_choose_duration)

internal val CommunityStrings.durationPermanent: String
  get() = t("appwin_community_duration_permanent", R.string.appwin_community_duration_permanent)

internal val CommunityStrings.durationOneDay: String
  get() = t("appwin_community_duration_one_day", R.string.appwin_community_duration_one_day)

internal val CommunityStrings.durationSevenDays: String
  get() = t("appwin_community_duration_seven_days", R.string.appwin_community_duration_seven_days)

internal val CommunityStrings.durationThirtyDays: String
  get() = t("appwin_community_duration_thirty_days", R.string.appwin_community_duration_thirty_days)

internal val CommunityStrings.motive: String
  get() = t("appwin_community_motive", R.string.appwin_community_motive)

internal val CommunityStrings.teamOnlyPlaceholder: String
  get() = t("appwin_community_team_only_placeholder", R.string.appwin_community_team_only_placeholder)

internal val CommunityStrings.notificationTitle: String
  get() = t("appwin_community_notification_title", R.string.appwin_community_notification_title)

internal val CommunityStrings.understood: String
  get() = t("appwin_community_understood", R.string.appwin_community_understood)

internal val CommunityStrings.postRemovedSection: String
  get() = t("appwin_community_post_removed_section", R.string.appwin_community_post_removed_section)

internal val CommunityStrings.commentRemovedSection: String
  get() = t("appwin_community_comment_removed_section", R.string.appwin_community_comment_removed_section)

internal val CommunityStrings.tempBanSection: String
  get() = t("appwin_community_temp_ban_section", R.string.appwin_community_temp_ban_section)

internal val CommunityStrings.banSection: String
  get() = t("appwin_community_ban_section", R.string.appwin_community_ban_section)

internal val CommunityStrings.banDefaultBody: String
  get() = t("appwin_community_ban_default_body", R.string.appwin_community_ban_default_body)

internal val CommunityStrings.permanentBanLabel: String
  get() = t("appwin_community_permanent_ban_label", R.string.appwin_community_permanent_ban_label)

internal val CommunityStrings.removedFallbackReason: String
  get() = t("appwin_community_removed_fallback_reason", R.string.appwin_community_removed_fallback_reason)

internal val CommunityStrings.postRemovedAlertTitle: String
  get() = t("appwin_community_post_removed_alert_title", R.string.appwin_community_post_removed_alert_title)

internal val CommunityStrings.commentRemovedAlertTitle: String
  get() = t("appwin_community_comment_removed_alert_title", R.string.appwin_community_comment_removed_alert_title)

internal val CommunityStrings.postRemovedAlertMessage: String
  get() = t("appwin_community_post_removed_alert_message", R.string.appwin_community_post_removed_alert_message)

internal val CommunityStrings.openModeration: String
  get() = t("appwin_community_open_moderation", R.string.appwin_community_open_moderation)

internal val CommunityStrings.openSanctions: String
  get() = t("appwin_community_open_sanctions", R.string.appwin_community_open_sanctions)

internal val CommunityStrings.anonymousBannerTitle: String
  get() = t("appwin_community_anonymous_banner_title", R.string.appwin_community_anonymous_banner_title)

internal val CommunityStrings.editProfileShort: String
  get() = t("appwin_community_edit_profile_short", R.string.appwin_community_edit_profile_short)

internal val CommunityStrings.anonymousPseudo: String
  get() = t("appwin_community_anonymous_pseudo", R.string.appwin_community_anonymous_pseudo)

internal val CommunityStrings.nicknamePlaceholder: String
  get() = t("appwin_community_nickname_placeholder", R.string.appwin_community_nickname_placeholder)

internal val CommunityStrings.dismiss: String
  get() = t("appwin_community_dismiss", R.string.appwin_community_dismiss)

internal val CommunityStrings.deleteCommentTitle: String
  get() = t("appwin_community_delete_comment_title", R.string.appwin_community_delete_comment_title)

internal val CommunityStrings.actionFailed: String
  get() = t("appwin_community_action_failed", R.string.appwin_community_action_failed)

internal fun CommunityStrings.reportsCount(count: Int): String =
  tf("appwin_community_reports_count", R.string.appwin_community_reports_count, count)

internal fun CommunityStrings.reportsCountOne(count: Int): String =
  tf("appwin_community_reports_count_one", R.string.appwin_community_reports_count_one, count)

internal fun CommunityStrings.removeInfo(name: String): String =
  tf("appwin_community_remove_info", R.string.appwin_community_remove_info, name)

internal fun CommunityStrings.hideInfo(name: String): String =
  tf("appwin_community_hide_info", R.string.appwin_community_hide_info, name)

internal fun CommunityStrings.warnInfo(name: String): String =
  tf("appwin_community_warn_info", R.string.appwin_community_warn_info, name)

internal fun CommunityStrings.visibleBy(name: String): String =
  tf("appwin_community_visible_by", R.string.appwin_community_visible_by, name)

internal fun CommunityStrings.shadowBanInfo(name: String): String =
  tf("appwin_community_shadow_ban_info", R.string.appwin_community_shadow_ban_info, name)

internal fun CommunityStrings.banInfo(name: String): String =
  tf("appwin_community_ban_info", R.string.appwin_community_ban_info, name)

internal fun CommunityStrings.characterCounter(count: Int, limit: Int): String =
  tf("appwin_community_character_counter", R.string.appwin_community_character_counter, count, limit)

internal fun CommunityStrings.warnSection(team: String): String =
  tf("appwin_community_warn_section", R.string.appwin_community_warn_section, team)

internal fun CommunityStrings.moderationTitle(count: Int): String =
  tf("appwin_community_moderation_title", R.string.appwin_community_moderation_title, count)

internal fun CommunityStrings.reportsAutoHidden(count: Int): String =
  tf("appwin_community_reports_auto_hidden", R.string.appwin_community_reports_auto_hidden, count)

internal fun CommunityStrings.tempBanLabel(date: String): String =
  tf("appwin_community_temp_ban_label", R.string.appwin_community_temp_ban_label, date)

internal fun CommunityStrings.duration(duration: SanctionDuration): String = when (duration) {
  SanctionDuration.PERMANENT -> durationPermanent
  SanctionDuration.ONE_DAY -> durationOneDay
  SanctionDuration.SEVEN_DAYS -> durationSevenDays
  SanctionDuration.THIRTY_DAYS -> durationThirtyDays
}

internal fun CommunityStrings.reactionLabel(kind: CommunityReactionKind): String {
  val key = "appwin_community_reaction_${kind.wire}"
  return t(key, REACTION_LABELS.getValue(kind))
}

internal fun CommunityStrings.reportReason(reason: CommunityReportReason): String = reasonLabel(reason.wire)

internal fun CommunityStrings.moderationReason(reason: ModerationReason): String = reasonLabel(reason.wire)

/** A reason key (report or moderation) as a label; a moderator's free text stays as typed. */
internal fun CommunityStrings.reasonLabel(raw: String): String {
  val resId = REASON_LABELS[raw] ?: return raw
  return t("appwin_community_reason_$raw", resId)
}

private val REACTION_LABELS = mapOf(
  CommunityReactionKind.LOVE to R.string.appwin_community_reaction_love,
  CommunityReactionKind.LIKE to R.string.appwin_community_reaction_like,
  CommunityReactionKind.LAUGH to R.string.appwin_community_reaction_laugh,
  CommunityReactionKind.FIRE to R.string.appwin_community_reaction_fire,
  CommunityReactionKind.WOW to R.string.appwin_community_reaction_wow,
  CommunityReactionKind.CLAP to R.string.appwin_community_reaction_clap,
  CommunityReactionKind.EYES to R.string.appwin_community_reaction_eyes,
  CommunityReactionKind.SAD to R.string.appwin_community_reaction_sad,
  CommunityReactionKind.PRAY to R.string.appwin_community_reaction_pray,
  CommunityReactionKind.BANGBANG to R.string.appwin_community_reaction_bangbang,
  CommunityReactionKind.ANGRY to R.string.appwin_community_reaction_angry,
)

private val REASON_LABELS = mapOf(
  "hate_speech" to R.string.appwin_community_reason_hate_speech,
  "sexual_content" to R.string.appwin_community_reason_sexual_content,
  "violence" to R.string.appwin_community_reason_violence,
  "spam" to R.string.appwin_community_reason_spam,
  "self_harm" to R.string.appwin_community_reason_self_harm,
  "child_safety" to R.string.appwin_community_reason_child_safety,
  "intellectual_property" to R.string.appwin_community_reason_intellectual_property,
  "impersonation" to R.string.appwin_community_reason_impersonation,
  "other" to R.string.appwin_community_reason_other,
  "dissatisfaction" to R.string.appwin_community_reason_dissatisfaction,
  "support" to R.string.appwin_community_reason_support,
  "personal_data" to R.string.appwin_community_reason_personal_data,
  "duplicate" to R.string.appwin_community_reason_duplicate,
  "harassment" to R.string.appwin_community_reason_harassment,
  "misinformation" to R.string.appwin_community_reason_misinformation,
  "off_topic" to R.string.appwin_community_reason_off_topic,
)

internal val CommunityStrings.deletePostTitle: String
  get() = t("appwin_community_delete_post_title", R.string.appwin_community_delete_post_title)

internal val CommunityStrings.deletePostMessage: String
  get() = t("appwin_community_delete_post_message", R.string.appwin_community_delete_post_message)
