package io.appwin.community.domain

// In-app moderation (Figma « Appwin InApp » 182:857 and its sheets): what
// moderators and admins see and decide. Mirror of the Swift entities.

/** Where a reported or held content stands. */
internal enum class ModerationContentStatus(val wire: String) {
  PUBLISHED("published"),
  PENDING("pending"),
  REMOVED("removed"),
  SCHEDULED("scheduled"),
  DRAFT("draft"),
  ;

  companion object {
    fun from(wire: String?): ModerationContentStatus =
      entries.firstOrNull { it.wire == wire } ?: PUBLISHED
  }
}

internal enum class ModerationTargetType(val wire: String) {
  POST("post"),
  COMMENT("comment"),
  PROFILE("profile"),
  ;

  companion object {
    fun from(wire: String?): ModerationTargetType? = entries.firstOrNull { it.wire == wire }
  }
}

internal data class ModerationReport(
  val id: String,
  /** Raw key: older reports may carry reasons the app no longer offers. */
  val reason: String,
  val createdAtMillis: Long,
)

/** One content awaiting a decision, drawn as the feed draws it. */
internal data class ModerationQueueItem(
  val targetType: ModerationTargetType,
  val targetId: String,
  val status: ModerationContentStatus,
  /** What the classifier flagged (`support`, `spam`…), empty when it let the content through. */
  val aiCategories: List<String>,
  val reports: List<ModerationReport>,
  /** The reported post, or the post the reported comment answers. */
  val post: CommunityPost,
  val comment: CommunityComment?,
) {
  val id: String get() = targetId
}

internal data class ModerationQueuePage(
  val items: List<ModerationQueueItem>,
  val pendingCount: Int,
  val hasMore: Boolean,
)

/** Decisions the app can take. Wire values are the API's. */
internal enum class ModerationAction(val wire: String) {
  APPROVE("approve"),
  HIDE_CONTENT("hide_content"),
  REMOVE_CONTENT("remove_content"),
  RESTORE_CONTENT("restore_content"),
  WARN("warn"),
  SHADOW_BAN("shadow_ban"),
  BAN("ban"),
}

/** Motifs offered when hiding or deleting (Figma 178:3480), sent as the decision's reason. */
internal enum class ModerationReason(val wire: String) {
  DISSATISFACTION("dissatisfaction"),
  SUPPORT("support"),
  PERSONAL_DATA("personal_data"),
  DUPLICATE("duplicate"),
  HATE_SPEECH("hate_speech"),
  SEXUAL_CONTENT("sexual_content"),
  VIOLENCE("violence"),
  SPAM("spam"),
  SELF_HARM("self_harm"),
  CHILD_SAFETY("child_safety"),
  INTELLECTUAL_PROPERTY("intellectual_property"),
  IMPERSONATION("impersonation"),
  OTHER("other"),
}

/** Ban and shadow ban lengths (Figma « Choisir la durée »). */
internal enum class SanctionDuration(val hours: Int?) {
  PERMANENT(null),
  ONE_DAY(24),
  SEVEN_DAYS(24 * 7),
  THIRTY_DAYS(24 * 30),
}

/** Pin settings of a post (Figma 178:3968): either criterion lifts the pin. */
internal data class PinSettings(
  val untilMillis: Long? = null,
  val maxViewsPerMember: Int? = null,
)

/** Categories that harm people read in red, the rest in amber (as on the dashboard). */
internal fun isHarmfulReason(reasonOrCategory: String): Boolean = reasonOrCategory in setOf(
  "harassment",
  "hate_speech",
  "sexual_content",
  "violence",
  "self_harm",
  "child_safety",
  "personal_data",
)
