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

  public companion object {
    public fun from(wire: String?): CommunityMemberRole =
      entries.firstOrNull { it.wire == wire } ?: MEMBER
  }
}

public enum class CommunityReactionKind(public val wire: String, public val emoji: String) {
  LIKE("like", "👍"),
  LOVE("love", "❤️"),
  LAUGH("laugh", "😂"),
  WOW("wow", "😮"),
  SAD("sad", "😢"),
  ;

  public companion object {
    /** `null` on an unknown value: a type added server-side is skipped. */
    public fun from(wire: String?): CommunityReactionKind? =
      entries.firstOrNull { it.wire == wire }
  }
}

public enum class CommunityReportReason(public val wire: String) {
  SPAM("spam"),
  HARASSMENT("harassment"),
  HATE_SPEECH("hate_speech"),
  SEXUAL_CONTENT("sexual_content"),
  VIOLENCE("violence"),
  MISINFORMATION("misinformation"),
  OFF_TOPIC("off_topic"),
  OTHER("other"),
}

public enum class CommunityNotificationType(public val wire: String) {
  POST_COMMENT("post_comment"),
  COMMENT_REPLY("comment_reply"),
  POST_REACTION("post_reaction"),
  COMMENT_REACTION("comment_reaction"),
  CONTENT_REMOVED("content_removed"),
  ADMIN_POST("admin_post"),
  ;

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
)

public data class CommunityMedia(
  public val url: String,
  public val width: Int? = null,
  public val height: Int? = null,
  public val alt: String? = null,
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
  public val isPinned: Boolean = false,
  public val hasAdminTag: Boolean = false,
  public val likeCount: Int = 0,
  public val commentCount: Int = 0,
  public val viewCount: Int = 0,
  public val myReaction: CommunityReactionKind? = null,
  public val canEdit: Boolean = false,
  public val canDelete: Boolean = false,
  public val isPendingReview: Boolean = false,
  /** Epoch milliseconds. `0` when the received date is unreadable. */
  public val publishedAtMillis: Long = 0,
  public val editedAtMillis: Long? = null,
)

public data class CommunityComment(
  public val id: String,
  public val postId: String,
  public val parentCommentId: String? = null,
  public val author: CommunityAuthor?,
  public val body: String,
  public val translatedBody: String? = null,
  public val sourceLanguage: String? = null,
  public val likeCount: Int = 0,
  public val replyCount: Int = 0,
  public val myReaction: CommunityReactionKind? = null,
  public val canEdit: Boolean = false,
  public val canDelete: Boolean = false,
  public val isPendingReview: Boolean = false,
  public val replies: List<CommunityComment> = emptyList(),
  public val createdAtMillis: Long = 0,
  public val editedAtMillis: Long? = null,
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
)

/** Result of a reaction toggle, enough to refresh a counter. */
public data class CommunityReactionResult(
  public val targetId: String,
  public val myReaction: CommunityReactionKind?,
  public val likeCount: Int,
)

/* -------------------------------------------------------------------------- */
/* Configuration                                                              */
/* -------------------------------------------------------------------------- */

public enum class CommunityFontFamily(public val wire: String) {
  SYSTEM("system"),
  ROUNDED("rounded"),
  SERIF("serif"),
  MONOSPACE("monospace"),
  CUSTOM("custom"),
  ;

  public companion object {
    public fun from(wire: String?): CommunityFontFamily =
      entries.firstOrNull { it.wire == wire } ?: SYSTEM
  }
}

public enum class CommunityFontScale(public val wire: String, public val scale: Float) {
  COMPACT("compact", 0.9f),
  DEFAULT("default", 1f),
  COMFORTABLE("comfortable", 1.08f),
  LARGE("large", 1.2f),
  ;

  public companion object {
    public fun from(wire: String?): CommunityFontScale =
      entries.firstOrNull { it.wire == wire } ?: DEFAULT
  }
}

public enum class CommunityRadius(public val wire: String, public val dp: Int) {
  LOW("low", 8),
  MEDIUM("medium", 14),
  HIGH("high", 20),
  MAX("max", 28),
  ;

  public companion object {
    public fun from(wire: String?): CommunityRadius =
      entries.firstOrNull { it.wire == wire } ?: HIGH
  }
}

public enum class CommunityColorScheme(public val wire: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark"),
  ;

  public companion object {
    public fun from(wire: String?): CommunityColorScheme =
      entries.firstOrNull { it.wire == wire } ?: SYSTEM
  }
}

public data class CommunityThemeConfig(
  public val primaryHex: String = "#F97316",
  public val primaryForegroundHex: String = "#FFFFFF",
  public val fontFamily: CommunityFontFamily = CommunityFontFamily.SYSTEM,
  public val fontFamilyName: String? = null,
  public val fontScale: CommunityFontScale = CommunityFontScale.DEFAULT,
  public val radius: CommunityRadius = CommunityRadius.HIGH,
  public val colorScheme: CommunityColorScheme = CommunityColorScheme.SYSTEM,
)

public data class CommunityFeatures(
  public val enabled: Boolean = false,
  public val postsEnabled: Boolean = true,
  public val commentsEnabled: Boolean = true,
  public val repliesEnabled: Boolean = true,
  public val imagesEnabled: Boolean = true,
  public val reactionsEnabled: Boolean = true,
  public val reactions: List<CommunityReactionKind> = listOf(CommunityReactionKind.LIKE),
  public val viewsEnabled: Boolean = true,
  public val authorEditEnabled: Boolean = true,
  public val translationEnabled: Boolean = false,
  public val profilesEnabled: Boolean = true,
  public val reportingEnabled: Boolean = true,
)

public data class CommunityLimits(
  public val postMaxLength: Int = 2000,
  public val commentMaxLength: Int = 1000,
  public val maxImagesPerPost: Int = 4,
  public val feedPreviewLines: Int = 6,
)

public data class CommunityContext(
  public val projectName: String = "",
  public val projectLogoUrl: String? = null,
)

public data class CommunityConfig(
  public val theme: CommunityThemeConfig = CommunityThemeConfig(),
  public val features: CommunityFeatures = CommunityFeatures(),
  public val limits: CommunityLimits = CommunityLimits(),
  public val context: CommunityContext = CommunityContext(),
  public val version: Int = 0,
)

/** Config, groupes, profil et pastille en un aller-retour. */
public data class CommunityBootstrap(
  public val config: CommunityConfig,
  public val groups: List<CommunityGroup>,
  public val profile: CommunityProfile,
  public val unreadNotificationCount: Int,
)

/** One feed page: the items and the next cursor, `null` at the end. */
public data class CommunityPage<T>(
  public val items: List<T>,
  public val nextCursor: String?,
)
