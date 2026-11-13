package io.appwin.community.domain

/**
 * Community domain entities.
 *
 * Strict mirror of the Swift entities: same names, same fields, same defaults. A
 * community must not behave differently per platform, and a divergence here is a
 * bug, not a variant.
 *
 * Network DTOs live apart (`data/`) and never cross this boundary: ISO dates,
 * URLs and enums are converted once, and anything unreadable degrades cleanly
 * rather than failing the whole page.
 */

public enum class CommunityMemberRole(public val wire: String) {
  MEMBER("member"),
  MODERATOR("moderator"),
  ADMIN("admin"),
  ;

  /** Moderators and admins decide on reported content from the app. */
  public val canModerate: Boolean
    get() = this != MEMBER

  public companion object {
    public fun from(wire: String?): CommunityMemberRole =
      entries.firstOrNull { it.wire == wire } ?: MEMBER
  }
}

/** In the order of the emoji bar (Figma 178:2456). */
public enum class CommunityReactionKind(public val wire: String, public val emoji: String) {
  LOVE("love", "❤️"),
  LIKE("like", "👍"),
  LAUGH("laugh", "😂"),
  FIRE("fire", "🔥"),
  WOW("wow", "😲"),
  CLAP("clap", "👏"),
  EYES("eyes", "👀"),
  SAD("sad", "😢"),
  PRAY("pray", "🙏"),
  BANGBANG("bangbang", "‼️"),
  ANGRY("angry", "😡"),
  ;

  public companion object {
    /** `null` on an unknown value: a type added server-side is skipped. */
    public fun from(wire: String?): CommunityReactionKind? =
      entries.firstOrNull { it.wire == wire }

    /**
     * Short-tap kind: heart (`LOVE`) when the studio offers it, else the first
     * configured kind. Picker `LIKE` stays 👍 and must not share this default.
     */
    public fun defaultTap(available: List<CommunityReactionKind>): CommunityReactionKind =
      available.firstOrNull { it == LOVE } ?: available.firstOrNull() ?: LOVE
  }
}

/** Report reasons offered to the member, in display order (Figma 155:873). */
public enum class CommunityReportReason(public val wire: String) {
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

public enum class CommunityNotificationType(public val wire: String) {
  POST_COMMENT("post_comment"),
  COMMENT_REPLY("comment_reply"),
  POST_REACTION("post_reaction"),
  COMMENT_REACTION("comment_reaction"),
  POLL_VOTE("poll_vote"),
  CONTENT_REMOVED("content_removed"),
  ADMIN_POST("admin_post"),
  ACCOUNT_WARNED("account_warned"),
  ACCOUNT_BANNED("account_banned"),
  ;

  /** Removed content, warnings, bans: shown behind the bell, never in the activity list. */
  public val isSanction: Boolean
    get() = this == CONTENT_REMOVED || this == ACCOUNT_WARNED || this == ACCOUNT_BANNED

  public companion object {
    /**
     * `null` on an unknown type: better to hide a notification than to show one
     * we cannot route on tap.
     */
    public fun from(wire: String?): CommunityNotificationType? =
      entries.firstOrNull { it.wire == wire }
  }
}

/** Feed sort. `RECENT` is the default: strictly reverse-chronological. */
public enum class CommunityFeedSort(public val wire: String) {
  RECENT("recent"),
  TOP("top"),
}

public data class CommunityAuthor(
  public val id: String,
  public val nickname: String,
  public val avatarUrl: String?,
  public val role: CommunityMemberRole,
  public val isTeam: Boolean,
) {
  /**
   * False for the sentinel id used when a snapshot remains but no profile is
   * addressable (erased member, legacy studio post without a profile).
   */
  public val isAddressable: Boolean
    get() = id != UNADDRESSABLE_ID

  public companion object {
    public const val UNADDRESSABLE_ID: String = "00000000-0000-0000-0000-000000000000"
  }
}

public enum class CommunityMediaType {
  IMAGE,
  VIDEO,
}

public data class CommunityMedia(
  public val url: String,
  public val width: Int? = null,
  public val height: Int? = null,
  public val alt: String? = null,
  public val type: CommunityMediaType = CommunityMediaType.IMAGE,
)

public val CommunityMedia.isVideo: Boolean
  get() = type == CommunityMediaType.VIDEO

public data class CommunityPollOption(
  public val id: String,
  public val text: String,
  public val voteCount: Int = 0,
)

public data class CommunityPoll(
  public val options: List<CommunityPollOption>,
  public val totalVotes: Int = 0,
  public val myOptionId: String? = null,
)

public data class CommunityPost(
  public val id: String,
  public val groupId: String,
  public val groupName: String,
  public val author: CommunityAuthor?,
  public val body: String,
  public val translatedBody: String? = null,
  public val sourceLanguage: String? = null,
  public val media: List<CommunityMedia> = emptyList(),
  public val poll: CommunityPoll? = null,
  public val isPinned: Boolean = false,
  public val hasAdminTag: Boolean = false,
  public val likeCount: Int = 0,
  public val commentCount: Int = 0,
  public val viewCount: Int = 0,
  public val myReaction: CommunityReactionKind? = null,
  /** Up to 3 most frequent reactions on the post; drives the summary emojis. */
  public val topReactions: List<CommunityReactionKind> = emptyList(),
  /** Full per-kind breakdown (sorted by volume), for the tap-to-detail sheet. */
  public val reactionCounts: List<CommunityReactionCount> = emptyList(),
  public val canEdit: Boolean = false,
  public val canDelete: Boolean = false,
  public val isPendingReview: Boolean = false,
  /** Epoch milliseconds. `0` when the received date is unreadable. */
  public val publishedAtMillis: Long = 0,
  public val editedAtMillis: Long? = null,
  /** Removed by moderation as it was written; its author learns why behind the bell. */
  public val isRemoved: Boolean = false,
)

/** One reaction kind with its count on a post or comment. */
public data class CommunityReactionCount(
  public val kind: CommunityReactionKind,
  public val count: Int,
)

/**
 * Local preview of a reaction toggle before the server answers.
 *
 * Must update [reactionCounts] and re-derive [topReactions]: filtering
 * [topReactions] alone empties the summary when the toggled kind was the only
 * entry, which flashed the UI fallback heart until the round trip landed.
 */
internal data class OptimisticReactionState(
  val myReaction: CommunityReactionKind?,
  val topReactions: List<CommunityReactionKind>,
  val reactionCounts: List<CommunityReactionCount>,
  val likeCount: Int,
)

internal fun optimisticReactionState(
  myReaction: CommunityReactionKind?,
  reactionCounts: List<CommunityReactionCount>,
  likeCount: Int,
  kind: CommunityReactionKind,
): OptimisticReactionState {
  val removing = myReaction == kind
  val counts = linkedMapOf<CommunityReactionKind, Int>()
  for (entry in reactionCounts) {
    if (entry.count > 0) counts[entry.kind] = entry.count
  }
  when {
    removing -> {
      val next = (counts[kind] ?: 1) - 1
      if (next <= 0) counts.remove(kind) else counts[kind] = next
    }
    myReaction != null -> {
      val previous = myReaction
      val previousNext = (counts[previous] ?: 1) - 1
      if (previousNext <= 0) counts.remove(previous) else counts[previous] = previousNext
      counts[kind] = (counts[kind] ?: 0) + 1
    }
    else -> counts[kind] = (counts[kind] ?: 0) + 1
  }
  val nextCounts = counts.entries
    .sortedByDescending { it.value }
    .map { CommunityReactionCount(it.key, it.value) }
  val nextLike = when {
    removing -> (likeCount - 1).coerceAtLeast(0)
    myReaction != null -> likeCount
    else -> likeCount + 1
  }
  return OptimisticReactionState(
    myReaction = if (removing) null else kind,
    topReactions = nextCounts.take(3).map { it.kind },
    reactionCounts = nextCounts,
    likeCount = nextLike,
  )
}

public data class CommunityComment(
  public val id: String,
  public val postId: String,
  public val parentCommentId: String? = null,
  public val author: CommunityAuthor?,
  public val body: String,
  public val media: List<CommunityMedia> = emptyList(),
  public val translatedBody: String? = null,
  public val sourceLanguage: String? = null,
  public val likeCount: Int = 0,
  public val replyCount: Int = 0,
  public val myReaction: CommunityReactionKind? = null,
  public val topReactions: List<CommunityReactionKind> = emptyList(),
  public val reactionCounts: List<CommunityReactionCount> = emptyList(),
  public val canEdit: Boolean = false,
  public val canDelete: Boolean = false,
  public val isPendingReview: Boolean = false,
  public val replies: List<CommunityComment> = emptyList(),
  public val createdAtMillis: Long = 0,
  public val editedAtMillis: Long? = null,
  public val isRemoved: Boolean = false,
)

public data class CommunityProfile(
  public val id: String,
  public val nickname: String,
  public val bio: String? = null,
  public val avatarUrl: String? = null,
  public val role: CommunityMemberRole = CommunityMemberRole.MEMBER,
  public val isTeam: Boolean = false,
  public val isAnonymous: Boolean = true,
  public val postCount: Int = 0,
  public val commentCount: Int = 0,
  public val receivedReactionCount: Int = 0,
  public val joinedAtMillis: Long = 0,
  public val isMe: Boolean = false,
  public val isBanned: Boolean = false,
) {
  public val canModerate: Boolean
    get() = role.canModerate

  public companion object {
    /** Shown in the feed header before bootstrap answers (same idea as iOS). */
    public val Placeholder: CommunityProfile = CommunityProfile(
      id = "",
      nickname = "",
      isAnonymous = true,
      isMe = true,
    )
  }
}

/** Confirmed community upload ready to attach as post media or avatar. */
public data class CommunityUploadedMedia(
  public val publicUrl: String,
  public val mimeType: String,
  public val sizeBytes: Int,
  public val width: Int? = null,
  public val height: Int? = null,
)

public data class CommunityGroup(
  public val id: String,
  public val name: String,
  public val description: String? = null,
  public val emoji: String? = null,
  public val imageUrl: String? = null,
  public val isDefault: Boolean = false,
  public val canPost: Boolean = true,
  public val postCount: Int = 0,
)

public data class CommunityNotification(
  public val id: String,
  public val type: CommunityNotificationType,
  public val actorId: String? = null,
  public val actorNickname: String? = null,
  public val actorAvatarUrl: String? = null,
  public val targetType: String,
  public val targetId: String,
  public val postId: String? = null,
  public val excerpt: String? = null,
  public val isRead: Boolean = false,
  public val createdAtMillis: Long = 0,
  /** Sanctions: a reason key (`violence`…) or the moderator's words; a warning's message. */
  public val reason: String? = null,
  /** Temporary ban: when it ends, epoch milliseconds. */
  public val sanctionUntilMillis: Long? = null,
)

/** Result of a reaction toggle, enough to refresh a counter. */
public data class CommunityReactionResult(
  public val targetId: String,
  public val myReaction: CommunityReactionKind?,
  public val topReactions: List<CommunityReactionKind>,
  public val reactionCounts: List<CommunityReactionCount> = emptyList(),
  public val likeCount: Int,
)

/** Config, groupes, profil et pastille en un aller-retour. */
public data class CommunityBootstrap(
  public val config: CommunityConfig,
  public val groups: List<CommunityGroup>,
  public val profile: CommunityProfile,
  public val unreadNotificationCount: Int,
  /** Removed content, warnings, bans not yet acknowledged: the bell. */
  public val unreadSanctionCount: Int = 0,
  /** Content awaiting a decision, for moderators and admins: the flag. */
  public val moderationPendingCount: Int = 0,
)

/** One feed page: the items and the next cursor, `null` at the end. */
public data class CommunityPage<T>(
  public val items: List<T>,
  public val nextCursor: String?,
)
