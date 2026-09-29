package io.appwin.core.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persistent, encrypted storage for the SDK's secrets: device id and session
 * token.
 *
 * The Android equivalent of the iOS keychain, with **one difference worth
 * knowing**. The keychain survives an uninstall; on Android nothing guarantees
 * that. These preferences are included in auto backup (`allowBackup`, on by
 * default), so the id comes back after a reinstall **if** backup is enabled on
 * the device. Otherwise the user starts with a new device id, and so a new
 * anonymous profile. This is documented rather than hidden: claiming it stable
 * would give false hope about conversation continuity.
 *
 * The fallback to plaintext preferences is not laziness: on some devices the
 * hardware keystore is broken and `EncryptedSharedPreferences` throws on open.
 * Without a fallback the SDK would refuse to start on those devices - an
 * unencrypted device id is a lesser evil than an app that will not launch.
 */
internal class SecureStore(context: Context) {
  private val prefs: SharedPreferences = openPrefs(context.applicationContext)

  fun get(key: String): String? = prefs.getString(key, null)

  fun set(key: String, value: String) {
    prefs.edit().putString(key, value).apply()
  }

  fun delete(key: String) {
    prefs.edit().remove(key).apply()
  }

  private companion object {
    const val TAG = "AppwinCore"
    const val ENCRYPTED_FILE = "appwin_core_secure"
    const val PLAIN_FILE = "appwin_core"

    fun openPrefs(context: Context): SharedPreferences =
      try {
        val masterKey = MasterKey.Builder(context)
          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
          .build()

        EncryptedSharedPreferences.create(
          context,
          ENCRYPTED_FILE,
          masterKey,
          EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
          EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
      } catch (error: Exception) {
        Log.w(TAG, "Encrypted storage unavailable, falling back to plain preferences", error)
        context.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
      }
  }
}
