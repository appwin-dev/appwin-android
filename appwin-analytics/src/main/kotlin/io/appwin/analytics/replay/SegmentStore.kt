package io.appwin.analytics.replay

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The segment being recorded, kept on disk frame by frame: `info.json`, one
 * JPEG per frame named after its offset in ms, and `events.jsonl` for taps
 * and screens. Never uploaded as such; if the process dies, the next launch
 * encodes what is here, which is what keeps the seconds before a crash.
 * Confined to the recorder's worker thread.
 */
internal class SegmentStore(private val root: File) {
  internal class Recorded(
    val info: SegmentInfo,
    val frames: List<Pair<Long, File>>,
    val screens: List<ReplayScreen>,
    val touches: List<ReplayTouch>,
  )

  fun open(info: SegmentInfo): File? = runCatching {
    val dir = File(root, "${info.sessionId}_${info.seq}")
    dir.deleteRecursively()
    dir.mkdirs()
    File(dir, INFO).writeText(info.toJson().toString())
    dir
  }.getOrNull()

  fun writeFrame(dir: File, offsetMs: Long, bitmap: Bitmap): Boolean = runCatching {
    val tmp = File(dir, "$offsetMs$FRAME.tmp")
    FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    tmp.renameTo(File(dir, "$offsetMs$FRAME"))
  }.getOrDefault(false)

  fun appendTouch(dir: File, touch: ReplayTouch) = append(
    dir,
    buildJsonObject {
      put("t", touch.t)
      put("x", touch.x)
      put("y", touch.y)
    }.toString(),
  )

  fun appendScreen(dir: File, screen: ReplayScreen) = append(
    dir,
    buildJsonObject {
      put("t", screen.t)
      put("name", screen.name)
    }.toString(),
  )

  fun read(dir: File): Recorded? {
    val info = runCatching { json.parseToJsonElement(File(dir, INFO).readText()).jsonObject }
      .getOrNull()
      ?.let(SegmentInfo::fromJson)
      ?: return null
    val frames = (dir.listFiles() ?: emptyArray())
      .filter { it.name.endsWith(FRAME) }
      .mapNotNull { file -> file.name.removeSuffix(FRAME).toLongOrNull()?.let { it to file } }
      .sortedBy { it.first }
    val screens = ArrayList<ReplayScreen>()
    val touches = ArrayList<ReplayTouch>()
    runCatching { File(dir, EVENTS).readLines() }.getOrDefault(emptyList()).forEach { line ->
      // A torn last line from a killed process is skipped, not fatal.
      val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
      val t = obj["t"]?.jsonPrimitive?.long ?: return@forEach
      val name = obj["name"]?.jsonPrimitive?.content
      if (name != null) {
        screens.add(ReplayScreen(t, name))
      } else {
        val x = runCatching { obj.getValue("x").jsonPrimitive.double }.getOrNull() ?: return@forEach
        val y = runCatching { obj.getValue("y").jsonPrimitive.double }.getOrNull() ?: return@forEach
        touches.add(ReplayTouch(t, x, y))
      }
    }
    return Recorded(info, frames, screens, touches)
  }

  fun leftovers(): List<File> = root.listFiles()?.filter { it.isDirectory } ?: emptyList()

  fun delete(dir: File) {
    dir.deleteRecursively()
  }

  fun purge() {
    root.deleteRecursively()
  }

  private fun append(dir: File, line: String) {
    runCatching { File(dir, EVENTS).appendText(line + "\n") }
  }

  private companion object {
    const val INFO = "info.json"
    const val EVENTS = "events.jsonl"
    const val FRAME = ".jpg"
    const val JPEG_QUALITY = 90
    val json = Json { ignoreUnknownKeys = true }
  }
}
