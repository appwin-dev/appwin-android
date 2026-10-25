package io.appwin.core.analytics

import io.appwin.core.consent.ConsentPurpose
import io.appwin.core.consent.ConsentState
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The one source of truth for consent: one persisted answer per purpose, read
 * from the prefs every time. No cache, no in-process override: a writer and a
 * reader on the same prefs always agree, whatever thread or coroutine turn
 * they run on.
 *
 * Writes (and the 0.15.0 migration) run under a process-wide lock, so two
 * quick `setConsent` calls land in call order. Readers that must never wait
 * (the crash handler, on a dying thread) use [stateWithoutBlocking].
 *
 * Per install on purpose: prefs (not the keystore) so a reinstall starts back
 * at the default, [ConsentState.GRANTED] (opt-out, the PostHog model): most
 * studios fall under the first-party audience-measurement exemption. A studio
 * with a consent screen sets [ConsentState.UNKNOWN] before it and relays the
 * answer.
 */
internal class ConsentStore(
  private val prefs: AnalyticsPrefs,
  private val keyPrefix: String,
) {
  /** Last value seen under the lock; only for [stateWithoutBlocking]. */
  private val lastSeen = java.util.concurrent.ConcurrentHashMap<ConsentPurpose, ConsentState>()

  init {
    lock.withLock {
      migrateLegacy()
      ConsentPurpose.entries.forEach { read(it) }
    }
  }

  fun state(purpose: ConsentPurpose): ConsentState = lock.withLock {
    migrateLegacy()
    read(purpose)
  }

  /**
   * For the crash handler: the lock is never waited for. When a write is in
   * flight, answers the last value this store saw rather than block a dying
   * thread.
   */
  fun stateWithoutBlocking(purpose: ConsentPurpose): ConsentState {
    if (!lock.tryLock()) return lastSeen[purpose] ?: ConsentState.GRANTED
    try {
      return read(purpose)
    } finally {
      lock.unlock()
    }
  }

  /** [purpose] `null` sets the three at once. */
  fun set(state: ConsentState, purpose: ConsentPurpose?) {
    lock.withLock {
      migrateLegacy()
      for (target in purpose?.let(::listOf) ?: ConsentPurpose.entries) {
        prefs.putString(key(target), state.wire)
        lastSeen[target] = state
      }
    }
  }

  /** Purposes in the order of [ConsentPurpose.entries], read in one hold of the lock. */
  val grantedPurposes: List<ConsentPurpose>
    get() = lock.withLock { migrateLegacy(); ConsentPurpose.entries.filter { read(it) == ConsentState.GRANTED } }

  val allDenied: Boolean
    get() = lock.withLock { migrateLegacy(); ConsentPurpose.entries.all { read(it) == ConsentState.DENIED } }

  val anyUnknown: Boolean
    get() = lock.withLock { migrateLegacy(); ConsentPurpose.entries.any { read(it) == ConsentState.UNKNOWN } }

  /** All three answers, read together: a decision and its stamp see the same values. */
  fun snapshot(): Map<ConsentPurpose, ConsentState> = lock.withLock {
    migrateLegacy()
    ConsentPurpose.entries.associateWith { read(it) }
  }

  private fun key(purpose: ConsentPurpose) = "${keyPrefix}consent.${purpose.wire}"

  private fun read(purpose: ConsentPurpose): ConsentState {
    val value = prefs.getString(key(purpose))?.let { ConsentState.fromWire(it) } ?: ConsentState.GRANTED
    lastSeen[purpose] = value
    return value
  }

  /**
   * 0.15.0 stored one `consent` key. Its answer is copied to the purposes
   * that have none yet (an existing answer is never overwritten), then the
   * key is erased: an app that had refused stays refused on the three.
   */
  private fun migrateLegacy() {
    val legacyKey = "${keyPrefix}consent"
    val legacy = prefs.getString(legacyKey) ?: return
    for (purpose in ConsentPurpose.entries) {
      if (prefs.getString(key(purpose)) == null) prefs.putString(key(purpose), legacy)
    }
    prefs.putString(legacyKey, null)
  }

  private companion object {
    /** Process-wide: two stores over the same prefs must still serialize. */
    val lock = ReentrantLock()
  }
}
