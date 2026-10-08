package io.appwin.community.domain

// Studio configuration of the community, as the dashboard sets it.

public enum class CommunityFontFamily(public val wire: String) {
  INTER("inter"),
  SYSTEM("system"),
  ROUNDED("rounded"),
  SERIF("serif"),
  MONOSPACE("monospace"),
  CUSTOM("custom"),
  ;

  public companion object {
    public fun from(wire: String?): CommunityFontFamily =
      entries.firstOrNull { it.wire == wire } ?: INTER
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

/**
 * Corner radius of the cards and pills. `HIGH` is the mock's own `post-card`
 * radius brought back to device dp, like every other measurement taken off
 * that frame - the community feed is rounder than the rest of the SDK on
 * purpose.
 */
public enum class CommunityRadius(public val wire: String, public val dp: Int) {
  LOW("low", 8),
  MEDIUM("medium", 15),
  HIGH("high", 23),
  MAX("max", 33),
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
    // Light when absent or unknown: the SDK was light-only, and an app must not
    // turn dark without the studio choosing it.
    public fun from(wire: String?): CommunityColorScheme =
      entries.firstOrNull { it.wire == wire } ?: LIGHT
  }
}

/** Gray family, coolest to warmest (Tailwind slate..stone). */
public enum class CommunityGrayWarmth(public val wire: String) {
  SLATE("slate"),
  GRAY("gray"),
  ZINC("zinc"),
  NEUTRAL("neutral"),
  STONE("stone"),
  ;

  public companion object {
    public fun from(wire: String?): CommunityGrayWarmth =
      entries.firstOrNull { it.wire == wire } ?: SLATE
  }
}

public data class CommunityThemeConfig(
  public val primaryHex: String = "#FA7315",
  public val primaryForegroundHex: String = "#FFFFFF",
  /** Accent surfaces are a two-stop gradient instead of a flat fill. */
  public val autoGradient: Boolean = false,
  /** Start stop of the gradient; `null` derives it from the accent. */
  public val gradientHex: String? = null,
  /** Drop shadow under the compose and send buttons. */
  public val buttonShadow: Boolean = true,
  /** `null` = the accent's dark shade. */
  public val buttonShadowHex: String? = null,
  /** `null` falls back to the project name - what the studio wants by default. */
  public val headerTitle: String? = null,
  public val headerTitleVisible: Boolean = true,
  public val fontFamily: CommunityFontFamily = CommunityFontFamily.INTER,
  public val fontFamilyName: String? = null,
  public val fontScale: CommunityFontScale = CommunityFontScale.DEFAULT,
  public val radius: CommunityRadius = CommunityRadius.HIGH,
  public val colorScheme: CommunityColorScheme = CommunityColorScheme.LIGHT,
  // Appended, not inserted: public API, positional calls must keep compiling.
  public val grayWarmth: CommunityGrayWarmth = CommunityGrayWarmth.SLATE,
)

public data class CommunityFeatures(
  public val enabled: Boolean = false,
  public val postsEnabled: Boolean = true,
  public val commentsEnabled: Boolean = true,
  public val repliesEnabled: Boolean = true,
  public val imagesEnabled: Boolean = true,
  public val reactionsEnabled: Boolean = true,
  public val reactions: List<CommunityReactionKind> = CommunityReactionKind.entries,
  public val viewsEnabled: Boolean = true,
  public val authorEditEnabled: Boolean = true,
  public val translationEnabled: Boolean = false,
  public val profilesEnabled: Boolean = true,
  public val reportingEnabled: Boolean = true,
  /** Who may post images, videos and polls, and who may comment. */
  public val permissions: CommunityPermissions = CommunityPermissions(),
) {
  /** The emoji bar on long press: only when the studio offers more than the heart. */
  public val offersEmojiReactions: Boolean
    get() = reactionsEnabled && reactions.size > 1
}

/** Who may do something in the community, set by the studio per capability. */
public enum class CommunityAudience(public val wire: String) {
  NOBODY("nobody"),
  ADMINS("admins"),
  EVERYONE("everyone"),
  ;

  /** `ADMINS` covers moderators too: they act for the studio inside the app. */
  public fun allows(role: CommunityMemberRole): Boolean = when (this) {
    EVERYONE -> true
    NOBODY -> false
    ADMINS -> role.canModerate
  }

  public companion object {
    public fun from(wire: String?): CommunityAudience =
      entries.firstOrNull { it.wire == wire } ?: EVERYONE
  }
}

public data class CommunityPermissions(
  public val images: CommunityAudience = CommunityAudience.EVERYONE,
  public val videos: CommunityAudience = CommunityAudience.EVERYONE,
  public val polls: CommunityAudience = CommunityAudience.EVERYONE,
  public val comments: CommunityAudience = CommunityAudience.EVERYONE,
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
