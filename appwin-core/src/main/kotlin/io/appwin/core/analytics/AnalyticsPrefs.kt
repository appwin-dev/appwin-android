package io.appwin.core.analytics

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny string-only key-value seam over SharedPreferences, so session and
 * consent logic can be tested with an in-memory map. Numbers are stored as
 * strings: one type keeps the seam trivial on both platforms.
 */
internal interface AnalyticsPrefs {
  fun getString(key: String): String?
  fun putString(key: String, value: String?)
}

internal class SharedPrefsAnalyticsPrefs(context: Context) : AnalyticsPrefs {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("appwin.analytics", Context.MODE_PRIVATE)

  override fun getString(key: String): String? = prefs.getString(key, null)

  override fun putString(key: String, value: String?) {
    prefs.edit().apply {
      if (value == null) remove(key) else putString(key, value)
    }.apply()
  }
}

internal class InMemoryAnalyticsPrefs : AnalyticsPrefs {
  private val values = HashMap<String, String>()

  override fun getString(key: String): String? = values[key]

  override fun putString(key: String, value: String?) {
    if (value == null) values.remove(key) else values[key] = value
  }
}
