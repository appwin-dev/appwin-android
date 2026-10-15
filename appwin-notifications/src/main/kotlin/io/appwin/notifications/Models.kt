package io.appwin.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class InAppMessage(
  public val id: String,
  public val campaignId: String,
  public val deliveryId: String,
  public val channel: String,
  public val content: InAppContent,
  public val format: String = "modal",
)

@Serializable
public data class InAppContent(
  public val title: String? = null,
  public val body: String? = null,
  public val imageUrl: String? = null,
  public val deeplink: String? = null,
  public val buttons: List<InAppButton>? = null,
)

@Serializable
public data class InAppButton(
  public val label: String,
  public val action: String,
  public val url: String? = null,
)

public enum class InAppButtonAction(public val wireValue: String) {
  DEEPLINK("deeplink"),
  DISMISS("dismiss"),
  OPT_IN_PUSH("opt_in_push"),
  OPEN_SETTINGS("open_settings"),
}

public enum class AutomationEvent(public val wireValue: String) {
  APP_OPEN("app_open"),
  APP_BACKGROUND("app_background"),
  PURCHASE("purchase"),
  CUSTOM_EVENT("custom_event"),
  PUSH_OPT_IN("push_opt_in"),
  SESSION_START("session_start"),
}

public enum class TrackEvent(public val wireValue: String) {
  OPENED("opened"),
  CLICKED("clicked"),
  DISMISSED("dismissed"),
}

@Serializable
internal data class PushTokenBody(
  val token: String,
  val platform: String,
  val pushOptIn: Boolean,
)

@Serializable
internal data class AutomationEventBody(
  val event: String,
  val eventName: String? = null,
  val properties: Map<String, String>? = null,
)

@Serializable
internal data class TrackBody(
  val deliveryId: String,
  val event: String,
  val buttonIndex: Int? = null,
)
