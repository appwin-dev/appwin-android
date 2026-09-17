package io.appwin.community.ui

import android.content.Context
import io.appwin.community.R

/**
 * Community labels, resolved from the **device locale**.
 *
 * The AAR ships `values` (English) and `values-fr` (French). Android picks the
 * right table from the phone language. A studio can still override any key by
 * defining the same `appwin_community_*` name in the host app - that wins.
 */
internal class CommunityStrings(private val context: Context) {

  private fun t(key: String, resId: Int): String {
    val hostId = context.resources.getIdentifier(key, "string", context.packageName)
    if (hostId != 0) {
      return runCatching { context.getString(hostId) }.getOrElse { context.getString(resId) }
    }
    return context.getString(resId)
  }

  private fun tf(key: String, resId: Int, vararg args: Any): String {
    val hostId = context.resources.getIdentifier(key, "string", context.packageName)
    if (hostId != 0) {
      return runCatching { context.getString(hostId, *args) }
        .getOrElse { context.getString(resId, *args) }
    }
    return context.getString(resId, *args)
  }

  // General
  val teamBadge: String get() = t("appwin_community_team_badge", R.string.appwin_community_team_badge)
  val retry: String get() = t("appwin_community_retry", R.string.appwin_community_retry)
  val cancel: String get() = t("appwin_community_cancel", R.string.appwin_community_cancel)
  val delete: String get() = t("appwin_community_delete", R.string.appwin_community_delete)
  val send: String get() = t("appwin_community_send", R.string.appwin_community_send)
  val close: String get() = t("appwin_community_close", R.string.appwin_community_close)
  val seeMore: String get() = t("appwin_community_see_more", R.string.appwin_community_see_more)
  val seeLess: String get() = t("appwin_community_see_less", R.string.appwin_community_see_less)

  // Feed
  val title: String get() = t("appwin_community_title", R.string.appwin_community_title)
  val allGroups: String get() = t("appwin_community_all_groups", R.string.appwin_community_all_groups)
  val newPost: String get() = t("appwin_community_new_post", R.string.appwin_community_new_post)
  val emptyFeedTitle: String
    get() = t("appwin_community_empty_feed_title", R.string.appwin_community_empty_feed_title)
  val emptyFeedMessage: String
    get() = t("appwin_community_empty_feed_message", R.string.appwin_community_empty_feed_message)
  val disabledTitle: String
    get() = t("appwin_community_disabled_title", R.string.appwin_community_disabled_title)
  val disabledMessage: String
    get() = t("appwin_community_disabled_message", R.string.appwin_community_disabled_message)
  val loadErrorTitle: String
    get() = t("appwin_community_load_error_title", R.string.appwin_community_load_error_title)
  val loadErrorMessage: String
    get() = t("appwin_community_load_error_message", R.string.appwin_community_load_error_message)

  // Post
  val like: String get() = t("appwin_community_like", R.string.appwin_community_like)
  val comment: String get() = t("appwin_community_comment", R.string.appwin_community_comment)

  fun likeCount(count: Int): String =
    if (count <= 0) like
    else tf("appwin_community_like_count", R.string.appwin_community_like_count, count)

  fun commentCount(count: Int): String =
    if (count <= 0) comment
    else tf("appwin_community_comment_count", R.string.appwin_community_comment_count, count)

  fun commentCountShort(count: Int): String =
    tf("appwin_community_comment_count_short", R.string.appwin_community_comment_count_short, count)

  fun viewCountShort(count: Int): String =
    tf("appwin_community_view_count_short", R.string.appwin_community_view_count_short, count)

  val beFirstToReact: String
    get() = t("appwin_community_be_first_to_react", R.string.appwin_community_be_first_to_react)

  val comments: String get() = t("appwin_community_comments", R.string.appwin_community_comments)
  val reply: String get() = t("appwin_community_reply", R.string.appwin_community_reply)
  val report: String get() = t("appwin_community_report", R.string.appwin_community_report)
  val edited: String get() = t("appwin_community_edited", R.string.appwin_community_edited)
  val pendingReview: String
    get() = t("appwin_community_pending_review", R.string.appwin_community_pending_review)
  val noComments: String get() = t("appwin_community_no_comments", R.string.appwin_community_no_comments)
  val translate: String get() = t("appwin_community_translate", R.string.appwin_community_translate)
  val showOriginal: String
    get() = t("appwin_community_show_original", R.string.appwin_community_show_original)

  // Composer
  val composerPlaceholder: String
    get() = t("appwin_community_composer_placeholder", R.string.appwin_community_composer_placeholder)
  val commentPlaceholder: String
    get() = t("appwin_community_comment_placeholder", R.string.appwin_community_comment_placeholder)
  val publish: String get() = t("appwin_community_publish", R.string.appwin_community_publish)
  val group: String get() = t("appwin_community_group", R.string.appwin_community_group)

  // Profile
  val profile: String get() = t("appwin_community_profile", R.string.appwin_community_profile)
  val posts: String get() = t("appwin_community_posts", R.string.appwin_community_posts)
  val publications: String
    get() = t("appwin_community_publications", R.string.appwin_community_publications)
  val editProfile: String
    get() = t("appwin_community_edit_profile", R.string.appwin_community_edit_profile)
  val editProfileTitle: String
    get() = t("appwin_community_edit_profile_title", R.string.appwin_community_edit_profile_title)
  val addBio: String get() = t("appwin_community_add_bio", R.string.appwin_community_add_bio)
  val nickname: String get() = t("appwin_community_nickname", R.string.appwin_community_nickname)
  val nicknameHelp: String
    get() = t("appwin_community_nickname_help", R.string.appwin_community_nickname_help)
  val bio: String get() = t("appwin_community_bio", R.string.appwin_community_bio)
  val bioPlaceholder: String
    get() = t("appwin_community_bio_placeholder", R.string.appwin_community_bio_placeholder)
  val save: String get() = t("appwin_community_save", R.string.appwin_community_save)
  val anonymous: String get() = t("appwin_community_anonymous", R.string.appwin_community_anonymous)
  val addPhoto: String get() = t("appwin_community_add_photo", R.string.appwin_community_add_photo)
  val addVideo: String get() = t("appwin_community_add_video", R.string.appwin_community_add_video)
  val videoComingSoon: String
    get() = t("appwin_community_video_coming_soon", R.string.appwin_community_video_coming_soon)
  val addPoll: String get() = t("appwin_community_add_poll", R.string.appwin_community_add_poll)
  val addPollOption: String
    get() = t("appwin_community_add_poll_option", R.string.appwin_community_add_poll_option)
  val pollOption: String get() = t("appwin_community_poll_option", R.string.appwin_community_poll_option)
  val noPublicationsYet: String
    get() = t("appwin_community_no_publications_yet", R.string.appwin_community_no_publications_yet)
  val noPublicationsOther: String
    get() = t("appwin_community_no_publications_other", R.string.appwin_community_no_publications_other)
  val createFirstPost: String
    get() = t("appwin_community_create_first_post", R.string.appwin_community_create_first_post)
  val reactionsReceived: String
    get() = t("appwin_community_reactions_received", R.string.appwin_community_reactions_received)
  val bannedNotice: String
    get() = t("appwin_community_banned_notice", R.string.appwin_community_banned_notice)

  // Report
  val reportTitle: String
    get() = t("appwin_community_report_title", R.string.appwin_community_report_title)
  val reportSent: String
    get() = t("appwin_community_report_sent", R.string.appwin_community_report_sent)

  fun reportReason(wire: String): String = when (wire) {
    "spam" -> t("appwin_community_reason_spam", R.string.appwin_community_reason_spam)
    "harassment" -> t("appwin_community_reason_harassment", R.string.appwin_community_reason_harassment)
    "hate_speech" ->
      t("appwin_community_reason_hate_speech", R.string.appwin_community_reason_hate_speech)
    "sexual_content" ->
      t("appwin_community_reason_sexual_content", R.string.appwin_community_reason_sexual_content)
    "violence" -> t("appwin_community_reason_violence", R.string.appwin_community_reason_violence)
    "misinformation" ->
      t("appwin_community_reason_misinformation", R.string.appwin_community_reason_misinformation)
    "off_topic" -> t("appwin_community_reason_off_topic", R.string.appwin_community_reason_off_topic)
    else -> t("appwin_community_reason_other", R.string.appwin_community_reason_other)
  }

  fun relativeNow(): String =
    t("appwin_community_relative_now", R.string.appwin_community_relative_now)

  fun relativeMinutes(n: Int): String =
    context.getString(R.string.appwin_community_relative_minutes, n)

  fun relativeHours(n: Int): String =
    context.getString(R.string.appwin_community_relative_hours, n)

  fun relativeDays(n: Int): String =
    context.getString(R.string.appwin_community_relative_days, n)

  fun relativeWeeks(n: Int): String =
    context.getString(R.string.appwin_community_relative_weeks, n)
}
