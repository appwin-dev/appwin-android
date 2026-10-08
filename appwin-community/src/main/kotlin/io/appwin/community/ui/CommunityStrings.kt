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

  internal fun t(key: String, resId: Int): String {
    val hostId = context.resources.getIdentifier(key, "string", context.packageName)
    if (hostId != 0) {
      return runCatching { context.getString(hostId) }.getOrElse { context.getString(resId) }
    }
    return context.getString(resId)
  }

  internal fun tf(key: String, resId: Int, vararg args: Any): String {
    val hostId = context.resources.getIdentifier(key, "string", context.packageName)
    if (hostId != 0) {
      return runCatching { context.getString(hostId, *args) }
        .getOrElse { context.getString(resId, *args) }
    }
    return context.getString(resId, *args)
  }

  /** Singular for 0 and 1 (French UI expects "0 commentaire"). */
  private fun usesSingularForm(count: Int): Boolean = count in 0..1

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
  val unavailableTitle: String
    get() = t("appwin_community_unavailable_title", R.string.appwin_community_unavailable_title)
  val unavailableMessage: String
    get() = t("appwin_community_unavailable_message", R.string.appwin_community_unavailable_message)
  val loadErrorTitle: String
    get() = t("appwin_community_load_error_title", R.string.appwin_community_load_error_title)
  val loadErrorMessage: String
    get() = t("appwin_community_load_error_message", R.string.appwin_community_load_error_message)

  // Post
  val like: String get() = t("appwin_community_like", R.string.appwin_community_like)
  val comment: String get() = t("appwin_community_comment", R.string.appwin_community_comment)

  fun likeCount(count: Int): String =
    if (usesSingularForm(count)) {
      tf("appwin_community_like_count_one", R.string.appwin_community_like_count_one, count)
    } else {
      tf("appwin_community_like_count", R.string.appwin_community_like_count, count)
    }

  fun commentCount(count: Int): String =
    if (usesSingularForm(count)) {
      tf("appwin_community_comment_count_one", R.string.appwin_community_comment_count_one, count)
    } else {
      tf("appwin_community_comment_count", R.string.appwin_community_comment_count, count)
    }

  fun commentCountShort(count: Int): String =
    if (usesSingularForm(count)) {
      tf("appwin_community_comment_count_short_one", R.string.appwin_community_comment_count_short_one, count)
    } else {
      tf("appwin_community_comment_count_short", R.string.appwin_community_comment_count_short, count)
    }

  val seniority: String get() = t("appwin_community_seniority", R.string.appwin_community_seniority)

  /** Time since the member joined, in its largest whole unit ("4 ans", "3 mois"). */
  fun seniority(joinedAtMillis: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - joinedAtMillis) / 86_400_000L).coerceAtLeast(0).toInt()
    val (count, key) = when {
      days >= 365 -> days / 365 to "years"
      days >= 30 -> days / 30 to "months"
      else -> days to "days"
    }
    val (one, other) = when (key) {
      "years" -> R.string.appwin_community_seniority_years_one to R.string.appwin_community_seniority_years
      "months" -> R.string.appwin_community_seniority_months_one to R.string.appwin_community_seniority_months
      else -> R.string.appwin_community_seniority_days_one to R.string.appwin_community_seniority_days
    }
    return if (usesSingularForm(count)) {
      tf("appwin_community_seniority_${key}_one", one, count)
    } else {
      tf("appwin_community_seniority_$key", other, count)
    }
  }

  fun viewCountShort(count: Int): String =
    if (usesSingularForm(count)) {
      tf("appwin_community_view_count_short_one", R.string.appwin_community_view_count_short_one, count)
    } else {
      tf("appwin_community_view_count_short", R.string.appwin_community_view_count_short, count)
    }

  val beFirstToReact: String
    get() = t("appwin_community_be_first_to_react", R.string.appwin_community_be_first_to_react)

  fun reactionsTitle(total: Int): String =
    if (usesSingularForm(total)) {
      tf("appwin_community_reactions_title_one", R.string.appwin_community_reactions_title_one, total)
    } else {
      tf("appwin_community_reactions_title", R.string.appwin_community_reactions_title, total)
    }

  val comments: String get() = t("appwin_community_comments", R.string.appwin_community_comments)
  val replies: String get() = t("appwin_community_replies", R.string.appwin_community_replies)
  val reply: String get() = t("appwin_community_reply", R.string.appwin_community_reply)
  val replyToComment: String
    get() = t("appwin_community_reply_to_comment", R.string.appwin_community_reply_to_comment)

  fun replyingTo(nickname: String): String =
    tf("appwin_community_replying_to", R.string.appwin_community_replying_to, nickname)

  fun showReplies(count: Int): String =
    if (usesSingularForm(count)) {
      tf("appwin_community_show_replies_one", R.string.appwin_community_show_replies_one, count)
    } else {
      tf("appwin_community_show_replies", R.string.appwin_community_show_replies, count)
    }

  val report: String get() = t("appwin_community_report", R.string.appwin_community_report)
  val pinned: String get() = t("appwin_community_pinned", R.string.appwin_community_pinned)
  val edited: String get() = t("appwin_community_edited", R.string.appwin_community_edited)
  val noComments: String get() = t("appwin_community_no_comments", R.string.appwin_community_no_comments)
  val noCommentsHint: String
    get() = t("appwin_community_no_comments_hint", R.string.appwin_community_no_comments_hint)
  val commentTitle: String
    get() = t("appwin_community_comment_title", R.string.appwin_community_comment_title)
  val back: String get() = t("appwin_community_back", R.string.appwin_community_back)
  val translate: String get() = t("appwin_community_translate", R.string.appwin_community_translate)
  val showOriginal: String
    get() = t("appwin_community_show_original", R.string.appwin_community_show_original)

  // Composer
  val composerPlaceholder: String
    get() = t("appwin_community_composer_placeholder", R.string.appwin_community_composer_placeholder)
  val commentPlaceholder: String
    get() = t("appwin_community_comment_placeholder", R.string.appwin_community_comment_placeholder)
  val publish: String get() = t("appwin_community_publish", R.string.appwin_community_publish)
  val editPost: String get() = t("appwin_community_edit_post", R.string.appwin_community_edit_post)
  val edit: String get() = t("appwin_community_edit", R.string.appwin_community_edit)
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
  val bio: String get() = t("appwin_community_bio", R.string.appwin_community_bio)
  val bioPlaceholder: String
    get() = t("appwin_community_bio_placeholder", R.string.appwin_community_bio_placeholder)
  val save: String get() = t("appwin_community_save", R.string.appwin_community_save)
  val anonymous: String get() = t("appwin_community_anonymous", R.string.appwin_community_anonymous)
  val anonymousHint: String
    get() = t("appwin_community_anonymous_hint", R.string.appwin_community_anonymous_hint)
  val addPhoto: String get() = t("appwin_community_add_photo", R.string.appwin_community_add_photo)
  val addVideo: String get() = t("appwin_community_add_video", R.string.appwin_community_add_video)
  val videoComingSoon: String
    get() = t("appwin_community_video_coming_soon", R.string.appwin_community_video_coming_soon)
  val videoTooLarge: String
    get() = t("appwin_community_video_too_large", R.string.appwin_community_video_too_large)
  val addPoll: String get() = t("appwin_community_add_poll", R.string.appwin_community_add_poll)
  val addPollOption: String
    get() = t("appwin_community_add_poll_option", R.string.appwin_community_add_poll_option)
  val pollOption: String get() = t("appwin_community_poll_option", R.string.appwin_community_poll_option)

  fun pollVotes(count: Int): String =
    if (usesSingularForm(count)) {
      tf("appwin_community_poll_votes_one", R.string.appwin_community_poll_votes_one, count)
    } else {
      tf("appwin_community_poll_votes", R.string.appwin_community_poll_votes, count)
    }
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
  val reportSent: String
    get() = t("appwin_community_report_sent", R.string.appwin_community_report_sent)


  fun relativeNow(): String =
    t("appwin_community_relative_now", R.string.appwin_community_relative_now)

  // Prefer host overrides via `tf` so studios can keep feed timestamps
  // aligned with their own copy (and with iOS RelativeDateTimeFormatter).
  fun relativeMinutes(n: Int): String =
    tf("appwin_community_relative_minutes", R.string.appwin_community_relative_minutes, n)

  fun relativeHours(n: Int): String =
    tf("appwin_community_relative_hours", R.string.appwin_community_relative_hours, n)

  fun relativeDays(n: Int): String =
    tf("appwin_community_relative_days", R.string.appwin_community_relative_days, n)

  fun relativeWeeks(n: Int): String =
    tf("appwin_community_relative_weeks", R.string.appwin_community_relative_weeks, n)

  // In-app banner (mirrors CommunityPushService.copyFor)
  val notifSomeone: String
    get() = t("appwin_community_notif_someone", R.string.appwin_community_notif_someone)
  val notifTitleComment: String
    get() = t("appwin_community_notif_title_comment", R.string.appwin_community_notif_title_comment)
  val notifTitleReply: String
    get() = t("appwin_community_notif_title_reply", R.string.appwin_community_notif_title_reply)
  val notifTitleReaction: String
    get() = t("appwin_community_notif_title_reaction", R.string.appwin_community_notif_title_reaction)
  val notifTitlePoll: String
    get() = t("appwin_community_notif_title_poll", R.string.appwin_community_notif_title_poll)
  val notifBodyAdminPost: String
    get() = t("appwin_community_notif_body_admin_post", R.string.appwin_community_notif_body_admin_post)

  fun notifBodyCommented(name: String): String =
    tf("appwin_community_notif_body_commented", R.string.appwin_community_notif_body_commented, name)

  fun notifBodyCommentedExcerpt(name: String, excerpt: String): String =
    tf(
      "appwin_community_notif_body_commented_excerpt",
      R.string.appwin_community_notif_body_commented_excerpt,
      name,
      excerpt,
    )

  fun notifBodyReplied(name: String): String =
    tf("appwin_community_notif_body_replied", R.string.appwin_community_notif_body_replied, name)

  fun notifBodyRepliedExcerpt(name: String, excerpt: String): String =
    tf(
      "appwin_community_notif_body_replied_excerpt",
      R.string.appwin_community_notif_body_replied_excerpt,
      name,
      excerpt,
    )

  fun notifBodyLiked(name: String): String =
    tf("appwin_community_notif_body_liked", R.string.appwin_community_notif_body_liked, name)

  fun notifBodyLikedExcerpt(name: String, excerpt: String): String =
    tf(
      "appwin_community_notif_body_liked_excerpt",
      R.string.appwin_community_notif_body_liked_excerpt,
      name,
      excerpt,
    )

  fun notifBodyVoted(name: String): String =
    tf("appwin_community_notif_body_voted", R.string.appwin_community_notif_body_voted, name)
}
