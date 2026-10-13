package io.appwin.analytics.replay

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/** Everything replay keeps on the device, per project, so it can be wiped in one call. */
internal class ReplayStorage(context: Context, projectAppId: String) {
  private val root = File(context.filesDir, "appwin/replay/$projectAppId")
  val segments = SegmentStore(File(root, "frames"))
  val queue = ReplayQueue(File(root, "queue"))
  val encodeDir = File(root, "encode")
  val prefs: SharedPreferences =
    context.getSharedPreferences("appwin.replay.$projectAppId", Context.MODE_PRIVATE)

  fun purge() {
    root.deleteRecursively()
  }
}
