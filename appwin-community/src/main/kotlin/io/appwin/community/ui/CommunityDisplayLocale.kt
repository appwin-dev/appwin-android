package io.appwin.community.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Locale used for Community UI strings.
 *
 * Prefer the **device** preferred languages (not the host app's configuration
 * locale). Flutter / RN hosts often ship English-only `res/values`, so
 * `context.getString` would otherwise stay on English even when the phone is
 * French - same idea as iOS [AppwinDisplayLocale].
 */
internal fun communityStrings(context: Context): CommunityStrings {
  val device = LocaleList.getDefault().get(0) ?: Locale.getDefault()
  val config = Configuration(context.resources.configuration)
  config.setLocale(device)
  return CommunityStrings(context.createConfigurationContext(config))
}
