package io.appwin.core.identity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Host device metadata, captured once during configuration.
 *
 * Sent to the server for telemetry and diagnostics. Nothing beyond what the iOS
 * SDK reports: platform, model, OS version, app version. No advertising id, no
 * serial number.
 */
public data class DeviceInfo(
  public val platform: String,
  public val model: String,
  public val osVersion: String,
  public val appVersion: String?,
) {
  public companion object {
    public fun current(context: Context): DeviceInfo = DeviceInfo(
      platform = "android",
      model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
      osVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
      appVersion = readAppVersion(context),
    )

    private fun readAppVersion(context: Context): String? = try {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
      // Impossible en pratique : on interroge notre propre paquet. Le SDK
      // must not fall over for a diagnostic detail.
      null
    }
  }
}
