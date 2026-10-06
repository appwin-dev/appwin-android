package io.appwin.core.analytics.crash

import java.io.File
import java.io.FileOutputStream

/**
 * One file per report, `<crashId>.json`, sent on a later launch.
 *
 * Plain blocking I/O with no lock on purpose: [write] runs on the crashing
 * thread, in the last milliseconds of the process, and must neither suspend
 * nor wait on a lock the sender might hold. A temp file plus rename keeps a
 * half-written report (the process killed mid-write) out of the send path.
 */
internal class CrashStore(
  private val directory: File,
  private val maxReports: Int = MAX_REPORTS,
) {
  internal class Stored(val file: File, val json: String)

  /** Never throws: a failure here must not mask the crash being reported. */
  fun write(crashId: String, json: String): Boolean = try {
    directory.mkdirs()
    val tmp = File(directory, "$crashId$TMP_SUFFIX")
    FileOutputStream(tmp).use { out ->
      out.write(json.toByteArray(Charsets.UTF_8))
      // Force to disk: the default handler kills the process right after.
      out.fd.sync()
    }
    val moved = tmp.renameTo(File(directory, "$crashId$SUFFIX"))
    if (!moved) tmp.delete()
    enforceCap()
    moved
  } catch (_: Throwable) {
    false
  }

  /** Oldest first, at most [limit]. Stray temp files are cleaned up. */
  fun pending(limit: Int, nowMs: Long = System.currentTimeMillis()): List<Stored> {
    val files = directory.listFiles() ?: return emptyList()
    // Only old ones: a recent temp file may be a non-fatal being written now.
    files
      .filter { it.name.endsWith(TMP_SUFFIX) && nowMs - it.lastModified() > STALE_TMP_MS }
      .forEach { it.delete() }
    return reports(files).take(limit).mapNotNull { file ->
      val json = runCatching { file.readText() }.getOrNull()
      if (json.isNullOrBlank()) {
        file.delete()
        null
      } else {
        Stored(file, json)
      }
    }
  }

  fun count(): Int = reports(directory.listFiles() ?: emptyArray()).size

  fun delete(file: File) {
    runCatching { file.delete() }
  }

  fun purgeAll() {
    runCatching { directory.deleteRecursively() }
  }

  private fun enforceCap() {
    val reports = reports(directory.listFiles() ?: return)
    if (reports.size <= maxReports) return
    reports.take(reports.size - maxReports).forEach { it.delete() }
  }

  private fun reports(files: Array<File>): List<File> =
    files.filter { it.name.endsWith(SUFFIX) }.sortedWith(compareBy({ it.lastModified() }, { it.name }))

  companion object {
    const val MAX_REPORTS = 20
    private const val SUFFIX = ".json"
    private const val TMP_SUFFIX = ".json.tmp"
    private const val STALE_TMP_MS = 60_000L
  }
}
