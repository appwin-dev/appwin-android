package io.appwin.community.ui

import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinInitStatus
import io.appwin.core.availability.AppwinUnavailableReason

/**
 * What the debug alert says about a verdict. Developer-facing, so English only.
 *
 * @property code the verdict as the SDK logs it.
 */
internal data class UnavailableDebugInfo(
  val code: String,
  val message: String,
  val actionLabel: String,
  val actionUrl: String,
)

internal const val COMMUNITY_DOCS_URL = "https://appwin.io/docs/products/community"
internal const val DASHBOARD_URL = "https://dashboard.appwin.io"
internal const val PRICING_URL = "https://appwin.io/pricing"

/** Community open only because the host app is debuggable. */
internal val DEBUG_UNLOCKED_INFO = UnavailableDebugInfo(
  code = "ready (debug only)",
  message = "Unlocked for this debug build only. Your plan does not include " +
    "Community: release builds will show the unavailable screen.",
  actionLabel = "See plans",
  actionUrl = PRICING_URL,
)

/** `null` for a ready verdict: there is nothing to explain. */
internal fun unavailableDebugInfo(result: AppwinInitResult?): UnavailableDebugInfo? {
  if (result == null) {
    return UnavailableDebugInfo(
      code = "notInitialized",
      message = "AppwinCommunity.initialize() was not called. Call it after " +
        "configure(), before showing the feed.",
      actionLabel = "Open the docs",
      actionUrl = COMMUNITY_DOCS_URL,
    )
  }
  return when (result.status) {
    AppwinInitStatus.READY -> null
    AppwinInitStatus.NOT_CONFIGURED -> UnavailableDebugInfo(
      code = "notConfigured",
      message = "AppwinCore.configure() was not called. Call it at launch, " +
        "before AppwinCommunity.initialize().",
      actionLabel = "Open the docs",
      actionUrl = COMMUNITY_DOCS_URL,
    )
    AppwinInitStatus.UNKNOWN -> UnavailableDebugInfo(
      code = "unknown",
      message = "No answer from the server yet. Check the network and the SDK " +
        "base URL: the answer is cached after the first success.",
      actionLabel = "Open the docs",
      actionUrl = COMMUNITY_DOCS_URL,
    )
    AppwinInitStatus.UNAVAILABLE -> when (result.reason) {
      AppwinUnavailableReason.PLAN -> UnavailableDebugInfo(
        code = "unavailable(plan)",
        message = "Your plan does not include Community. Upgrade it to open the " +
          "feed in release builds.",
        actionLabel = "See plans",
        actionUrl = PRICING_URL,
      )
      else -> UnavailableDebugInfo(
        code = "unavailable(disabled)",
        message = "Community is switched off for this project. Turn it on in the " +
          "dashboard, under SDK: the feed opens without an app update.",
        actionLabel = "Open dashboard",
        actionUrl = DASHBOARD_URL,
      )
    }
  }
}
