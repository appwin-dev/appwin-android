package io.appwin.community.data

import io.appwin.community.domain.CommunityAudience
import io.appwin.community.domain.CommunityColorScheme
import io.appwin.community.domain.CommunityConfig
import io.appwin.community.domain.CommunityContext
import io.appwin.community.domain.CommunityFeatures
import io.appwin.community.domain.CommunityFontFamily
import io.appwin.community.domain.CommunityFontScale
import io.appwin.community.domain.CommunityGrayWarmth
import io.appwin.community.domain.CommunityLimits
import io.appwin.community.domain.CommunityPermissions
import io.appwin.community.domain.CommunityRadius
import io.appwin.community.domain.CommunityReactionKind
import io.appwin.community.domain.CommunityThemeConfig
import kotlinx.serialization.Serializable

// The studio configuration as the API serves it.

@Serializable
internal data class CommunityThemeDto(
  val primary: String = "#FA7315",
  val primaryForeground: String = "#FFFFFF",
  val autoGradient: Boolean? = null,
  val gradientColor: String? = null,
  val buttonShadow: Boolean? = null,
  val buttonShadowColor: String? = null,
  val headerTitle: String? = null,
  val headerTitleVisible: Boolean? = null,
  val fontFamily: String? = null,
  val fontFamilyName: String? = null,
  val fontScale: String? = null,
  val radius: String? = null,
  val colorScheme: String? = null,
  val grayWarmth: String? = null,
) {
  fun toDomain(): CommunityThemeConfig = CommunityThemeConfig(
    primaryHex = primary,
    primaryForegroundHex = primaryForeground,
    autoGradient = autoGradient ?: false,
    gradientHex = gradientColor,
    buttonShadow = buttonShadow ?: true,
    buttonShadowHex = buttonShadowColor,
    headerTitle = headerTitle,
    headerTitleVisible = headerTitleVisible ?: true,
    fontFamily = CommunityFontFamily.from(fontFamily),
    fontFamilyName = fontFamilyName,
    fontScale = CommunityFontScale.from(fontScale),
    radius = CommunityRadius.from(radius),
    colorScheme = CommunityColorScheme.from(colorScheme),
    grayWarmth = CommunityGrayWarmth.from(grayWarmth),
  )
}

@Serializable
internal data class CommunityFeaturesDto(
  val enabled: Boolean = false,
  val postsEnabled: Boolean = true,
  val commentsEnabled: Boolean = true,
  val repliesEnabled: Boolean = true,
  val imagesEnabled: Boolean = true,
  val reactionsEnabled: Boolean = true,
  val reactions: List<String> = CommunityReactionKind.entries.map { it.wire },
  val viewsEnabled: Boolean = true,
  val authorEditEnabled: Boolean = true,
  val translationEnabled: Boolean = false,
  val profilesEnabled: Boolean = true,
  val reportingEnabled: Boolean = true,
  val permissions: PermissionsDto? = null,
) {
  @Serializable
  data class PermissionsDto(
    val images: String? = null,
    val videos: String? = null,
    val polls: String? = null,
    val comments: String? = null,
  )

  fun toDomain(): CommunityFeatures = CommunityFeatures(
    enabled = enabled,
    postsEnabled = postsEnabled,
    commentsEnabled = commentsEnabled,
    repliesEnabled = repliesEnabled,
    imagesEnabled = imagesEnabled,
    reactionsEnabled = reactionsEnabled,
    // An unknown type is skipped rather than failing the decode of the whole
    // configuration.
    reactions = reactions.mapNotNull { CommunityReactionKind.from(it) },
    viewsEnabled = viewsEnabled,
    authorEditEnabled = authorEditEnabled,
    translationEnabled = translationEnabled,
    profilesEnabled = profilesEnabled,
    reportingEnabled = reportingEnabled,
    // An API older than permissions: the legacy booleans say who may post images and comment.
    permissions = CommunityPermissions(
      images = permissions?.images?.let(CommunityAudience::from)
        ?: if (imagesEnabled) CommunityAudience.EVERYONE else CommunityAudience.NOBODY,
      videos = CommunityAudience.from(permissions?.videos),
      polls = CommunityAudience.from(permissions?.polls),
      comments = permissions?.comments?.let(CommunityAudience::from)
        ?: if (commentsEnabled) CommunityAudience.EVERYONE else CommunityAudience.NOBODY,
    ),
  )
}

@Serializable
internal data class CommunityLimitsDto(
  val postMaxLength: Int = 2000,
  val commentMaxLength: Int = 1000,
  val maxImagesPerPost: Int = 4,
  val feedPreviewLines: Int = 6,
) {
  fun toDomain(): CommunityLimits =
    CommunityLimits(postMaxLength, commentMaxLength, maxImagesPerPost, feedPreviewLines)
}

@Serializable
internal data class CommunityContextDto(
  val projectName: String = "",
  val projectLogoUrl: String? = null,
) {
  fun toDomain(): CommunityContext =
    CommunityContext(projectName, projectLogoUrl?.takeIf { it.isNotBlank() })
}

@Serializable
internal data class CommunityConfigDto(
  val theme: CommunityThemeDto = CommunityThemeDto(),
  val features: CommunityFeaturesDto = CommunityFeaturesDto(),
  val limits: CommunityLimitsDto = CommunityLimitsDto(),
  val context: CommunityContextDto = CommunityContextDto(),
  val version: Int = 0,
) {
  fun toDomain(): CommunityConfig = CommunityConfig(
    theme = theme.toDomain(),
    features = features.toDomain(),
    limits = limits.toDomain(),
    context = context.toDomain(),
    version = version,
  )
}
