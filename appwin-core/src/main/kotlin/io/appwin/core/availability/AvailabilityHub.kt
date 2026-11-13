package io.appwin.core.availability

import android.util.Log
import io.appwin.core.AppwinInternalApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/** Last verdict per product, as a stream a mounted view can follow. */
@OptIn(AppwinInternalApi::class)
internal class AvailabilityHub {
  private val states = MutableStateFlow<Map<AppwinProduct, AppwinProductState>>(emptyMap())
  private val debugUnlockLogged = ConcurrentHashMap.newKeySet<AppwinProduct>()

  val known: Set<AppwinProduct>
    get() = states.value.keys

  /** One update: a collector never sees a verdict with the previous pause. */
  fun publish(product: AppwinProduct, result: AppwinInitResult, pause: AppwinPause? = null) {
    states.update { it + (product to AppwinProductState(result, pause)) }
  }

  /** The verdict and the pause of [product] together; same seeding as [updates]. */
  fun states(product: AppwinProduct, seed: suspend () -> Unit): Flow<AppwinProductState> =
    states
      .onStart { if (product !in states.value) seed() }
      .mapNotNull { it[product] }
      .distinctUntilChanged()

  /** The pause of [product], null outside a pause; same seeding as [updates]. */
  fun pauseUpdates(product: AppwinProduct, seed: suspend () -> Unit): Flow<AppwinPause?> =
    states(product, seed).map { it.pause }.distinctUntilChanged()

  /** [seed] runs when nothing is known yet for [product], and must end up publishing it. */
  fun updates(product: AppwinProduct, seed: suspend () -> Unit): Flow<AppwinInitResult> =
    states(product, seed).map { it.result }.distinctUntilChanged()

  fun reportDebugUnlock(product: AppwinProduct) {
    if (!debugUnlockLogged.add(product)) return
    Log.w(
      "Appwin",
      "${product.key} is unlocked because this is a debug build: the organisation's plan " +
        "does not include it, so release builds will get unavailable(plan).",
    )
  }

  fun reset() {
    states.value = emptyMap()
    debugUnlockLogged.clear()
  }
}
