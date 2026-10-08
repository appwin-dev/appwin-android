package io.appwin.analytics.replay

import java.io.File
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Encoded segments waiting for upload: `<name>.mp4` plus `<name>.json` (the
 * meta). The meta is written last, through a rename, so its presence is what
 * makes an entry complete. Bounded in bytes, oldest dropped first: a device
 * offline for days must not fill the user's storage with video.
 */
internal class ReplayQueue(
  private val directory: File,
  private val maxBytes: Long = ReplayLimits.QUEUE_MAX_BYTES,
  private val now: () -> Long = System::currentTimeMillis,
) {
  internal class Entry(val name: String, val meta: JsonObject, val video: File, val metaFile: File)

  @Synchronized
  fun enqueue(video: File, meta: JsonObject): Boolean {
    directory.mkdirs()
    val name = String.format(Locale.US, "%013d-%s", now(), video.nameWithoutExtension)
    val target = File(directory, "$name$VIDEO")
    if (!video.renameTo(target)) {
      runCatching { video.copyTo(target, overwrite = true) }.onFailure { return false }
      video.delete()
    }
    val tmp = File(directory, "$name$META.tmp")
    val written = runCatching {
      tmp.writeText(meta.toString())
      tmp.renameTo(File(directory, "$name$META"))
    }.getOrDefault(false)
    if (!written) {
      tmp.delete()
      target.delete()
      return false
    }
    enforceCap()
    return true
  }

  /** Oldest first. Incomplete leftovers past a minute are swept. */
  @Synchronized
  fun pending(): List<Entry> {
    val files = directory.listFiles() ?: return emptyList()
    val metas = files.filter { it.name.endsWith(META) }.sortedBy { it.name }
    val complete = metas.map { it.name.removeSuffix(META) }.toSet()
    files
      .filter { (it.name.endsWith(VIDEO) && it.name.removeSuffix(VIDEO) !in complete) || it.name.endsWith(".tmp") }
      .filter { now() - it.lastModified() > STALE_MS }
      .forEach { it.delete() }
    return metas.mapNotNull { metaFile ->
      val name = metaFile.name.removeSuffix(META)
      val video = File(directory, "$name$VIDEO")
      val meta = runCatching { json.parseToJsonElement(metaFile.readText()).jsonObject }.getOrNull()
      if (meta == null || !video.exists()) {
        metaFile.delete()
        video.delete()
        null
      } else {
        Entry(name, meta, video, metaFile)
      }
    }
  }

  @Synchronized
  fun remove(entry: Entry) {
    entry.metaFile.delete()
    entry.video.delete()
  }

  @Synchronized
  fun purge() {
    directory.deleteRecursively()
  }

  fun totalBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0L

  private fun enforceCap() {
    var total = totalBytes()
    if (total <= maxBytes) return
    val oldestFirst = directory.listFiles()
      ?.filter { it.name.endsWith(META) }
      ?.sortedBy { it.name }
      ?: return
    for (metaFile in oldestFirst) {
      if (total <= maxBytes) return
      val video = File(directory, metaFile.name.removeSuffix(META) + VIDEO)
      total -= metaFile.length() + video.length()
      metaFile.delete()
      video.delete()
    }
  }

  private companion object {
    const val VIDEO = ".mp4"
    const val META = ".json"
    const val STALE_MS = 60_000L
    val json = Json { ignoreUnknownKeys = true }
  }
}
