package io.appwin.analytics.replay

import android.content.SharedPreferences

/**
 * Segment numbering and recorded time of the current session, persisted: an
 * analytics session outlives a relaunch, and a reused `seq` would overwrite
 * a segment already uploaded (`(sessionId, seq)` is the server's dedup key).
 * Confined to the recorder's worker thread.
 */
internal class ReplaySessionBudget(private val prefs: SharedPreferences) {
  /** Committed synchronously: a crash right after must not hand the same number out again. */
  fun nextSeq(sessionId: String): Int {
    sync(sessionId)
    val seq = prefs.getInt(SEQ, 0)
    prefs.edit().putInt(SEQ, seq + 1).commit()
    return seq
  }

  fun isExhausted(sessionId: String): Boolean {
    sync(sessionId)
    return prefs.getInt(FRAMES, 0) >= ReplayLimits.MAX_SESSION_FRAMES
  }

  fun addFrame(sessionId: String) {
    sync(sessionId)
    prefs.edit().putInt(FRAMES, prefs.getInt(FRAMES, 0) + 1).apply()
  }

  private fun sync(sessionId: String) {
    if (prefs.getString(SESSION, null) == sessionId) return
    prefs.edit().putString(SESSION, sessionId).putInt(SEQ, 0).putInt(FRAMES, 0).commit()
  }

  private companion object {
    const val SESSION = "session"
    const val SEQ = "nextSeq"
    const val FRAMES = "frames"
  }
}
