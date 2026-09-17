package io.appwin.tiktok

import android.content.Context
import com.tiktok.TikTokBusinessSdk
import com.tiktok.appevents.base.TTBaseEvent
import io.appwin.core.attribution.AdSignalsAdapter

/**
 * TikTok App Events adapter (ADR-0038, exception acted 2026-09-05):
 * wraps the official TikTok Business SDK behind the appwin surface -
 * the studio only ever calls track(), Core drives this through the
 * ad-signals hub (advertising consent + dashboard wiring).
 *
 * Governance choices, per the ADR contract:
 * - Auto install/launch tracking stays ON: those are TikTok's own
 *   attribution signals and the adapter only ever runs under consent.
 * - Every relayed event carries the SAME event_id as our ingest, so a
 *   future server-side Events API source dedupes within TikTok's 48 h
 *   window.
 * - SKAN is iOS-only; the Android SDK has nothing to disable here.
 * - The TikTok SDK cannot be un-initialized: after a deactivate (consent
 *   withdrawn mid-session) we stop relaying, and the next launch simply
 *   never activates it.
 */
public class TikTokAdSignalsAdapter : AdSignalsAdapter {
  override val network: String = "tiktok"

  @Volatile private var running = false

  override fun activate(context: Context, config: Map<String, String>) {
    val tiktokAppId = config["appId"] ?: return
    // On Android the TikTok "app id" IS the package name (config's
    // storeAppId carries the iOS App Store id - not for us).
    var ttConfig = TikTokBusinessSdk.TTConfig(context, config["accessToken"])
      .setTTAppId(tiktokAppId)
      .setAppId(context.packageName)
    // Test-events mode (AppwinAttribution.setAdSignalsDebugMode): events
    // stream to the Events Manager test console and are excluded from
    // campaign optimisation.
    if (config["debugMode"] == "true") {
      ttConfig = ttConfig.openDebugMode()
    }
    TikTokBusinessSdk.initializeSdk(ttConfig)
    running = true
  }

  override fun deactivate() {
    running = false
  }

  override fun onEvent(name: String, eventId: String, props: Map<String, Any?>?) {
    if (!running) return
    val builder = TTBaseEvent.newBuilder(mapEventName(name), eventId)
    props?.forEach { (key, value) ->
      when (value) {
        is String -> builder.addProperty(key, value)
        is Boolean -> builder.addProperty(key, value)
        is Int -> builder.addProperty(key, value)
        is Long -> builder.addProperty(key, value)
        is Float -> builder.addProperty(key, value.toDouble())
        is Double -> builder.addProperty(key, value)
        else -> {}
      }
    }
    TikTokBusinessSdk.trackTTEvent(builder.build())
  }
}

/**
 * Our lower_snake names to TikTok's standard app event names; anything
 * else forwards under its own (custom) name.
 */
internal fun mapEventName(name: String): String = when (name) {
  "purchase" -> "Purchase"
  "start_trial" -> "StartTrial"
  "subscribe" -> "Subscribe"
  "complete_registration" -> "CompleteRegistration"
  else -> name
}
