@file:OptIn(AppwinInternalApi::class)

package io.appwin.attribution

import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.availability.AppwinInitResult
import io.appwin.core.availability.AppwinProduct
import io.appwin.core.availability.reportUnavailable

/** Re-exported so a studio using attribution never imports an `io.appwin.core.*` type by hand. */
public typealias AdvertisingConsent = io.appwin.core.attribution.AdvertisingConsent

/**
 * Appwin Attribution: the acquisition signals of an app - Play Install
 * Referrer, SKAdNetwork conversion values (iOS), advertising identity
 * (GAID/IDFA) and the optional embedded ad-network adapters (TikTok).
 *
 * A product in its own right, distinct from Analytics: it has its own
 * `initialize()` and its own dashboard availability. The only thing it
 * shares with Analytics is Core's event pipeline (the plumbing that
 * ingests events into ClickHouse), which neither product owns.
 *
 * No `isReady` gate is needed around the consent call: it is buffered
 * in any order, and the server verdict decides whether the machinery
 * actually starts.
 *
 * ```kotlin
 * AppwinCore.configure(this, projectAppId = "…")
 * AppwinAttribution.setAdvertisingConsent(AdvertisingConsent.GRANTED)
 * AppwinAttribution.initialize()
 * ```
 */
public object AppwinAttribution {

  /**
   * Starts the acquisition signals, availability permitting. Same
   * contract as the other products: the server verdict (plan, product
   * toggle) gates the start, cached on disk for an offline launch, and
   * repeated calls are cheap.
   */
  @JvmStatic
  public suspend fun initialize(): AppwinInitResult {
    val result = AppwinCore.availability(AppwinProduct.ATTRIBUTION)
    isReady = result.isReady
    if (!result.isReady) {
      reportUnavailable(AppwinProduct.ATTRIBUTION, result)
    } else {
      AppwinCore.startAttribution()
    }
    return result
  }

  /** Whether [initialize] has returned [AppwinInitResult.Ready]. */
  @JvmStatic
  public var isReady: Boolean = false
    private set

  /**
   * Advertising consent (opt-in): whether conversion signals may be
   * activated towards the ad networks and whether the advertising
   * identifier (GAID) may be collected. Relay your consent flow's
   * verdict here. Callable before [initialize] (buffered).
   *
   * The rule: this decides IF signals reach the networks at all; on iOS,
   * ATT decides only whether the IDFA enriches them.
   */
  @JvmStatic
  public fun setAdvertisingConsent(consent: AdvertisingConsent) {
    AppwinCore.setAdvertisingConsent(consent)
  }

  /**
   * Debug mode for the embedded ad-network adapters (TikTok test
   * events): events sent while enabled show up in real time in the
   * network's test console (TikTok Events Manager > Test event) - and
   * are flagged as TEST data, excluded from campaign optimisation.
   *
   * Call it BEFORE [initialize]: the network SDKs read the flag at
   * activation and cannot re-init once started. Never ship a release
   * build with this enabled.
   */
  @JvmStatic
  public fun setAdSignalsDebugMode(enabled: Boolean) {
    AppwinCore.setAdSignalsDebugMode(enabled)
  }
}
