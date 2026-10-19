package io.appwin.community.data

import android.content.Context
import io.appwin.community.domain.CommunityConfig
import io.appwin.core.AppwinCore
import kotlinx.serialization.json.Json

/**
 * Last config served by `/bootstrap`, on disk.
 *
 * Without it every cold mount rendered the default title ("Community") until
 * the network answered, then swapped to the studio's. The DTO is stored rather
 * than the domain model so the next launch replays the exact network decode.
 * Plain `SharedPreferences`: nothing in here is secret.
 */
internal object CommunityConfigCache {
  private const val PREFS = "io.appwin.community.config"
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

  private fun prefs() =
    AppwinCore.applicationContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun key(): String? = AppwinCore.projectAppId?.let { "config.$it" }

  fun read(): CommunityConfig? {
    val raw = key()?.let { prefs()?.getString(it, null) } ?: return null
    return runCatching { json.decodeFromString(CommunityConfigDto.serializer(), raw) }
      .getOrNull()
      ?.toDomain()
  }

  fun write(dto: CommunityConfigDto) {
    val key = key() ?: return
    runCatching { json.encodeToString(CommunityConfigDto.serializer(), dto) }
      .onSuccess { prefs()?.edit()?.putString(key, it)?.apply() }
  }
}
