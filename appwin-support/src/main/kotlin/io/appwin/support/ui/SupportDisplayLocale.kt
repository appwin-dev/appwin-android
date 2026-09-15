package io.appwin.support.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Locale for messenger date stamps and day labels.
 *
 * Prefer the customer's recognised language (identify / content detection) so
 * an EN speaker on a FR phone still sees "Today" and English clock formats.
 */
internal fun displayLocale(language: String?, context: Context): Locale {
  val raw =
    language
      ?.trim()
      ?.replace('_', '-')
      ?.takeIf { it.isNotEmpty() }
  if (raw != null) return Locale.forLanguageTag(raw)
  return appLocale(context)
}

internal fun supportStrings(context: Context, language: String?): SupportStrings {
  val locale = displayLocale(language, context)
  val config = Configuration(context.resources.configuration)
  config.setLocale(locale)
  return SupportStrings(context.createConfigurationContext(config))
}
