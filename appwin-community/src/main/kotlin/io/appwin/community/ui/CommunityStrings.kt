package io.appwin.community.ui

import android.content.Context

/**
 * SDK labels.
 *
 * The SDK is embedded in apps of many languages: hard-coding the strings would
 * make the feed English inside a French app.
 *
 * Resolution happens in the **host app's** resources, not the module's. Two
 * consequences, both intended:
 *
 * - the studio translates the SDK by adding the `appwin_community_*` keys to
 *   its own `strings.xml`, without waiting for us to ship its language;
 * - it can also rewrite a label that does not match its tone by redefining the
 *   key.
 *
 * With no resource supplied, the English default below is shown. Never a raw key
 * on screen.
 *
 * The mechanism is the exact counterpart of iOS's `NSLocalizedString`.
 */
internal class CommunityStrings(private val context: Context) {

  private fun t(key: String, fallback: String): String {
    val id = context.resources.getIdentifier(key, "string", context.packageName)
    return if (id == 0) fallback else runCatching { context.getString(id) }.getOrDefault(fallback)
  }

  // General
  val teamBadge: String get() = t("appwin_community_team_badge", "Team")
  val retry: String get() = t("appwin_community_retry", "Retry")
  val cancel: String get() = t("appwin_community_cancel", "Cancel")
  val delete: String get() = t("appwin_community_delete", "Delete")
  val send: String get() = t("appwin_community_send", "Send")
  val close: String get() = t("appwin_community_close", "Close")
  val seeMore: String get() = t("appwin_community_see_more", "See more")
  val seeLess: String get() = t("appwin_community_see_less", "See less")

  // Fil
  val title: String get() = t("appwin_community_title", "Community")
  val allGroups: String get() = t("appwin_community_all_groups", "For you")
  val newPost: String get() = t("appwin_community_new_post", "Create a post")
  val emptyFeedTitle: String get() = t("appwin_community_empty_feed_title", "Nothing here yet")
  val emptyFeedMessage: String
    get() = t(
      "appwin_community_empty_feed_message",
      "Be the first to share something with the community.",
    )
  val disabledTitle: String get() = t("appwin_community_disabled_title", "Coming soon")
  val disabledMessage: String
    get() = t(
      "appwin_community_disabled_message",
      "The community is not open yet. Come back a bit later.",
    )
  val loadErrorTitle: String get() = t("appwin_community_load_error_title", "Couldn't load")
  val loadErrorMessage: String
    get() = t("appwin_community_load_error_message", "Check your connection and try again.")

  // Post
  val like: String get() = t("appwin_community_like", "Like")
  val comment: String get() = t("appwin_community_comment", "Comment")
  val comments: String get() = t("appwin_community_comments", "Comments")
  val reply: String get() = t("appwin_community_reply", "Reply")
  val report: String get() = t("appwin_community_report", "Report")
  val edited: String get() = t("appwin_community_edited", "edited")
  val pendingReview: String
    get() = t("appwin_community_pending_review", "Pending review - only you can see it")
  val noComments: String get() = t("appwin_community_no_comments", "No comments yet")
  val translate: String get() = t("appwin_community_translate", "Translate")
  val showOriginal: String get() = t("appwin_community_show_original", "Show original")

  // Composer
  val composerPlaceholder: String
    get() = t("appwin_community_composer_placeholder", "Share something…")
  val commentPlaceholder: String
    get() = t("appwin_community_comment_placeholder", "Write a comment…")
  val publish: String get() = t("appwin_community_publish", "Publish")
  val group: String get() = t("appwin_community_group", "Group")

  // Profil
  val profile: String get() = t("appwin_community_profile", "Profile")
  val posts: String get() = t("appwin_community_posts", "Posts")
  val reactionsReceived: String get() = t("appwin_community_reactions_received", "Reactions")
  val bannedNotice: String
    get() = t("appwin_community_banned_notice", "You can read the feed but not post.")

  // Signalement
  val reportTitle: String get() = t("appwin_community_report_title", "Report this content")
  val reportSent: String get() = t("appwin_community_report_sent", "Thanks, we'll take a look.")

  fun reportReason(wire: String): String = when (wire) {
    "spam" -> t("appwin_community_reason_spam", "Spam")
    "harassment" -> t("appwin_community_reason_harassment", "Harassment")
    "hate_speech" -> t("appwin_community_reason_hate_speech", "Hate speech")
    "sexual_content" -> t("appwin_community_reason_sexual_content", "Sexual content")
    "violence" -> t("appwin_community_reason_violence", "Violence")
    "misinformation" -> t("appwin_community_reason_misinformation", "Misinformation")
    "off_topic" -> t("appwin_community_reason_off_topic", "Off topic")
    else -> t("appwin_community_reason_other", "Something else")
  }
}
