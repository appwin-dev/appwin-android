package io.appwin.core.availability

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/** Last verdict per product, as a stream a mounted view can follow. */
internal class AvailabilityHub {
  private val verdicts = MutableStateFlow<Map<AppwinProduct, AppwinInitResult>>(emptyMap())
  private val debugUnlockLogged = ConcurrentHashMap.newKeySet<AppwinProduct>()

  val known: Set<AppwinProduct>
    get() = verdicts.value.keys

  fun publish(product: AppwinProduct, result: AppwinInitResult) {
    verdicts.update { it + (product to result) }
  }

  /** [seed] runs when nothing is known yet for [product], and must end up publishing it. */
  fun updates(product: AppwinProduct, seed: suspend () -> Unit): Flow<AppwinInitResult> =
    verdicts
      .onStart { if (product !in verdicts.value) seed() }
      .mapNotNull { it[product] }
      .distinctUntilChanged()

  fun reportDebugUnlock(product: AppwinProduct) {
    if (!debugUnlockLogged.add(product)) return
    Log.w(
      "Appwin",
      "${product.key} is unlocked because this is a debug build: the organisation's plan " +
        "does not include it, so release builds will get unavailable(plan).",
    )
  }

  fun reset() {
    verdicts.value = emptyMap()
    debugUnlockLogged.clear()
  }
}
