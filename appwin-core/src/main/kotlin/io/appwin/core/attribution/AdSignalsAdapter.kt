package io.appwin.core.attribution

import android.content.Context

/**
 * Contract an ad-network adapter module implements (ADR-0038, revised
 * 2026-09-05: the TikTok App Events SDK ships as an internal adapter of
 * the appwin SDK, in its own optional module). The studio never touches
 * this: adding the module to the build is the whole integration, Core
 * discovers it via ServiceLoader and drives its lifecycle.
 *
 * Rules the implementation must honour:
 * - activate only ever fires with the advertising consent granted AND
 *   the network wired in the dashboard (remote config);
 * - the adapter's own SKAN handling stays disabled - Core v2 is the
 *   single conversion-value manager;
 * - onEvent receives the SAME eventId our ingest stores, so the network
 *   can dedupe against any server-side source of the same event.
 */
public interface AdSignalsAdapter {
  /** Network key, matching the dashboard destination ('tiktok'). */
  public val network: String

  /** Config = the dashboard-declared wiring for this app (ids, never secrets). */
  public fun activate(context: Context, config: Map<String, String>)

  /** Consent withdrawn or network unwired: stop emitting, release what you can. */
  public fun deactivate()

  public fun onEvent(name: String, eventId: String, props: Map<String, Any?>?)
}
