package io.appwin.core.analytics

import kotlin.math.max

/**
 * Application sessions for analytics: UUIDv7 id, inactivity timeout, and the
 * reserved lifecycle events (`app_install`, `app_update`, `session_start`,
 * `session_end` with `duration_ms`).
 *
 * State is persisted so a `session_end` can be emitted retroactively after a
 * process kill: on the next launch, a stale persisted session ends at its
 * last recorded activity, not at the relaunch instant. NOT thread-safe: the
 * EventPipeline coroutine is the only caller.
 */
internal class SessionManager(
  private val prefs: AnalyticsPrefs,
  private val keyPrefix: String,
  private val appVersion: String?,
  private val timeoutMs: Long,
  private val maxAgeMs: Long,
  private val now: () -> Long = System::currentTimeMillis,
) {
  internal data class StandardEvent(
    val name: String,
    val sessionId: String,
    val occurredAtMs: Long,
    val durationMs: Long?,
  )

  var sessionId: String? = prefs.getString(keyPrefix + "session.id")
    private set
  private var startedAtMs: Long? = readMs("session.startedAt")
  private var lastActiveAtMs: Long? = readMs("session.lastActiveAt")
  private var lastPersistedActiveAtMs: Long? = null

  /**
   * Called before any event is queued and on foreground. Returns the
   * reserved events to queue first, and guarantees [sessionId] is set.
   */
  fun touch(): List<StandardEvent> {
    val at = now()
    val events = ArrayList<StandardEvent>(3)

    val currentId = sessionId
    val startedAt = startedAtMs
    val lastActiveAt = lastActiveAtMs
    if (currentId != null && startedAt != null && lastActiveAt != null) {
      val expired = at - lastActiveAt > timeoutMs || at - startedAt > maxAgeMs
      if (!expired) {
        lastActiveAtMs = at
        persistActivityIfDue(at)
        return emptyList()
      }
      events.add(
        StandardEvent(
          name = "session_end",
          sessionId = currentId,
          occurredAtMs = lastActiveAt,
          durationMs = max(0, lastActiveAt - startedAt),
        ),
      )
    }

    val newId = Uuid7.generate(nowMs = at)

    // Install and update lead the timeline of the session they open.
    if (prefs.getString(keyPrefix + "installTracked") == null) {
      prefs.putString(keyPrefix + "installTracked", "1")
      events.add(StandardEvent("app_install", newId, at, null))
    } else {
      val lastVersion = prefs.getString(keyPrefix + "lastVersion")
      if (appVersion != null && lastVersion != null && lastVersion != appVersion) {
        events.add(StandardEvent("app_update", newId, at, null))
      }
    }
    prefs.putString(keyPrefix + "lastVersion", appVersion)

    sessionId = newId
    startedAtMs = at
    lastActiveAtMs = at
    persistSession(at)
    events.add(StandardEvent("session_start", newId, at, null))
    return events
  }

  /**
   * App left the foreground: record the activity boundary now, it is what
   * a retroactive `session_end` will use after a kill.
   */
  fun onBackground() {
    val at = now()
    lastActiveAtMs = at
    persistSession(at)
  }

  /**
   * Consent denied: forget the session, keep the install/version marks
   * (a later re-consent must not re-emit `app_install`).
   */
  fun reset() {
    sessionId = null
    startedAtMs = null
    lastActiveAtMs = null
    lastPersistedActiveAtMs = null
    prefs.putString(keyPrefix + "session.id", null)
    prefs.putString(keyPrefix + "session.startedAt", null)
    prefs.putString(keyPrefix + "session.lastActiveAt", null)
  }

  private fun persistSession(at: Long) {
    prefs.putString(keyPrefix + "session.id", sessionId)
    startedAtMs?.let { prefs.putString(keyPrefix + "session.startedAt", it.toString()) }
    prefs.putString(keyPrefix + "session.lastActiveAt", at.toString())
    lastPersistedActiveAtMs = at
  }

  private fun persistActivityIfDue(at: Long) {
    val last = lastPersistedActiveAtMs
    if (last != null && at - last < PERSIST_ACTIVE_INTERVAL_MS) return
    prefs.putString(keyPrefix + "session.lastActiveAt", at.toString())
    lastPersistedActiveAtMs = at
  }

  private fun readMs(suffix: String): Long? = prefs.getString(keyPrefix + suffix)?.toLongOrNull()

  private companion object {
    /**
     * Re-persisting `lastActiveAt` on every event would hammer the disk;
     * a 10 s granularity is invisible next to a 30 min timeout.
     */
    const val PERSIST_ACTIVE_INTERVAL_MS = 10_000L
  }
}
