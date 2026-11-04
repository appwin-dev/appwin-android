package io.appwin.core.analytics

import android.util.Log
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Disk queue for analytics events. NOT thread-safe on purpose: the
 * EventPipeline coroutine is the only caller and serializes every access.
 *
 * Layout: `current.jsonl` receives appends, one wire-format event per line;
 * at [maxBatch] lines, or when a flush starts, it is atomically renamed to
 * `ready-<ms>-<seq>.jsonl` (zero-padded, so lexical order is rotation
 * order even for two rotations in the same millisecond). One ready file =
 * one POST, deleted on success. JSONL keeps the crash blast radius to the
 * last line, and the rename keeps a half-written batch out of the send path.
 */
internal class EventStore(
  private val directory: File,
  private val maxBatch: Int,
  private val maxQueueEvents: Int,
) {
  internal class ReadyBatch(val file: File, val lines: List<String>)

  private var currentLineCount = 0
  private val readyLineCounts = HashMap<String, Int>()
  private var rotationSeq = 0

  /** Events currently on disk (current + ready). Overflow control input. */
  var queuedEventCount: Int = 0
    private set

  private val currentFile: File get() = File(directory, "current.jsonl")

  /**
   * Creates the directory and rebuilds the counters from what a previous
   * launch left behind. Called once, off the main thread.
   */
  fun start() {
    runCatching { directory.mkdirs() }
      .onFailure { Log.w(TAG, "analytics store: cannot create $directory", it) }
    currentLineCount = lineCount(currentFile)
    readyLineCounts.clear()
    for (file in readyFiles()) {
      readyLineCounts[file.name] = lineCount(file)
    }
    recomputeTotal()
  }

  /**
   * Appends one wire-format event line. Returns how many older events were
   * dropped to stay under [maxQueueEvents] (0 in the nominal case).
   */
  fun append(line: String): Int {
    runCatching { currentFile.appendText(line + "\n") }
      .onFailure {
        Log.w(TAG, "analytics store: append failed", it)
        return 0
      }
    currentLineCount += 1
    recomputeTotal()
    if (currentLineCount >= maxBatch) rotateCurrent()
    return enforceLimit()
  }

  /** Makes the current file a ready batch (atomic rename). No-op when empty. */
  fun rotateCurrent() {
    if (currentLineCount == 0) return
    val name = "ready-%013d-%04d.jsonl".format(System.currentTimeMillis(), rotationSeq)
    rotationSeq += 1
    val moved = runCatching { currentFile.renameTo(File(directory, name)) }.getOrDefault(false)
    if (!moved) {
      Log.w(TAG, "analytics store: rotate failed")
      return
    }
    readyLineCounts[name] = currentLineCount
    currentLineCount = 0
    recomputeTotal()
  }

  /**
   * Oldest ready batch, with corrupt lines dropped individually and fully
   * unreadable files deleted. Null when nothing is ready.
   */
  fun nextReadyBatch(): ReadyBatch? {
    for (file in readyFiles()) {
      val content = runCatching { file.readText() }.getOrNull()
      if (content == null) {
        Log.w(TAG, "analytics store: unreadable batch ${file.name}, dropping it")
        delete(file)
        continue
      }
      val lines = content.split("\n").filter { it.isNotBlank() && isValidEventLine(it) }
      if (lines.isEmpty()) {
        delete(file)
        continue
      }
      return ReadyBatch(file, lines)
    }
    return null
  }

  fun delete(file: File) {
    runCatching { file.delete() }
    readyLineCounts.remove(file.name)
    recomputeTotal()
  }

  /** Consent denied: everything goes. */
  fun purgeAll() {
    runCatching { directory.deleteRecursively() }
    currentLineCount = 0
    readyLineCounts.clear()
    queuedEventCount = 0
    runCatching { directory.mkdirs() }
  }

  private fun readyFiles(): List<File> =
    (directory.list() ?: emptyArray())
      .filter { it.startsWith("ready-") && it.endsWith(".jsonl") }
      .sorted()
      .map { File(directory, it) }

  private fun enforceLimit(): Int {
    var dropped = 0
    while (queuedEventCount > maxQueueEvents) {
      val oldest = readyFiles().firstOrNull() ?: break
      dropped += readyLineCounts[oldest.name] ?: 0
      Log.w(TAG, "analytics store: queue over $maxQueueEvents, dropping ${oldest.name}")
      delete(oldest)
    }
    return dropped
  }

  private fun isValidEventLine(line: String): Boolean =
    runCatching { Json.parseToJsonElement(line) is JsonObject }.getOrDefault(false)

  private fun lineCount(file: File): Int {
    val content = runCatching { file.readText() }.getOrNull() ?: return 0
    return content.split("\n").count { it.isNotBlank() }
  }

  private fun recomputeTotal() {
    queuedEventCount = currentLineCount + readyLineCounts.values.sum()
  }

  private companion object {
    const val TAG = "Appwin"
  }
}
