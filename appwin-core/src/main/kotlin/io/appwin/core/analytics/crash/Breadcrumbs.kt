package io.appwin.core.analytics.crash

import java.util.concurrent.atomic.AtomicReference

/**
 * The last [CAPACITY] screens and events, names only (zero PII, ADR-0056).
 *
 * Copy-on-write behind an [AtomicReference] rather than a lock: the crash
 * handler snapshots it from the dying thread, which must never wait on a
 * lock some other thread holds.
 */
internal class Breadcrumbs(private val now: () -> Long = System::currentTimeMillis) {
  private val entries = AtomicReference<List<CrashReport.Breadcrumb>>(emptyList())

  @Volatile
  var currentScreen: String? = null
    private set

  fun screen(name: String) {
    currentScreen = name
    add(CrashReport.Breadcrumb(now(), CrashReport.Breadcrumb.Type.SCREEN, name))
  }

  fun event(name: String) {
    add(CrashReport.Breadcrumb(now(), CrashReport.Breadcrumb.Type.EVENT, name))
  }

  fun snapshot(): List<CrashReport.Breadcrumb> = entries.get()

  fun clear() {
    entries.set(emptyList())
    currentScreen = null
  }

  private fun add(crumb: CrashReport.Breadcrumb) {
    while (true) {
      val current = entries.get()
      val next = (current + crumb).takeLast(CAPACITY)
      if (entries.compareAndSet(current, next)) return
    }
  }

  companion object {
    const val CAPACITY = 20
  }
}
