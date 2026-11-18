package io.appwin.core.analytics.crash

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
  private val context = CrashContext(
    appVersion = "1.2.0",
    appBuild = "42",
    os = "Android 15",
    model = "Google Pixel 9",
    sdkVersion = "0.9.2",
  )

  private fun report(
    frames: List<CrashReport.Frame> = emptyList(),
    message: String? = "boom",
    breadcrumbs: List<CrashReport.Breadcrumb> = emptyList(),
  ) = CrashReport(
    crashId = "0190f5d2-1c3a-7b4e-9f00-000000000001",
    kind = CrashReport.Kind.NON_FATAL,
    occurredAtMs = 1_700_000_000_123,
    sessionId = "0190f5d2-1c3a-7b4e-9f00-000000000002",
    screen = "home",
    exceptionType = "java.lang.IllegalStateException",
    exceptionMessage = message,
    frames = frames,
    context = context,
    breadcrumbs = breadcrumbs,
  )

  @Test
  fun `serializes with the contract field names`() {
    val json = report(
      frames = listOf(CrashReport.Frame("com.acme.Foo.bar", "Foo.kt", 12, "com.acme", true)),
      breadcrumbs = listOf(
        CrashReport.Breadcrumb(1_700_000_000_000, CrashReport.Breadcrumb.Type.SCREEN, "home"),
      ),
    ).toJson()

    assertEquals(
      setOf(
        "crashId", "kind", "runtime", "occurredAt", "sessionId", "screen", "exception",
        "frames", "app", "device", "sdkVersion", "breadcrumbs",
      ),
      json.keys,
    )
    assertEquals("non_fatal", json.string("kind"))
    assertEquals("android", json.string("runtime"))
    assertEquals("2023-11-14T22:13:20.123Z", json.string("occurredAt"))
    val exception = json["exception"]!!.jsonObject
    assertEquals("java.lang.IllegalStateException", exception.string("type"))
    assertEquals("boom", exception.string("message"))
    val frame = json["frames"]!!.jsonArray[0].jsonObject
    assertEquals(setOf("fn", "file", "line", "module", "inApp"), frame.keys)
    assertEquals(12, frame["line"]!!.jsonPrimitive.int)
    assertTrue(frame["inApp"]!!.jsonPrimitive.boolean)
    assertEquals(setOf("version", "build"), json["app"]!!.jsonObject.keys)
    assertEquals(setOf("os", "model"), json["device"]!!.jsonObject.keys)
    val crumb = json["breadcrumbs"]!!.jsonArray[0].jsonObject
    assertEquals(setOf("at", "type", "name"), crumb.keys)
    assertEquals("screen", crumb.string("type"))
  }

  @Test
  fun `applies the contract bounds`() {
    val frames = List(250) { CrashReport.Frame("a.B.c$it", null, null, null, false) }
    val json = report(frames = frames, message = "x".repeat(5_000)).toJson()
    assertEquals(200, json["frames"]!!.jsonArray.size)
    assertEquals(1024, json["exception"]!!.jsonObject.string("message").length)
  }

  @Test
  fun `omits absent optional fields`() {
    val json = report(message = null).toJson()
    assertNull(json["exception"]!!.jsonObject["message"])
    assertNull(json["breadcrumbs"])
  }

  @Test
  fun `classifies in-app frames by package prefix`() {
    val classifier = InAppClassifier(listOf("com.acme.app", "com.acme.shared."))
    assertTrue(classifier.isInApp("com.acme.app.MainActivity"))
    assertTrue(classifier.isInApp("com.acme.shared.Util"))
    assertFalse(classifier.isInApp("com.acme.application.Other"))
    assertFalse(classifier.isInApp("android.app.Activity"))
    assertFalse(classifier.isInApp("okhttp3.Call"))
  }

  @Test
  fun `never classifies the SDK as in-app, even under its namespace`() {
    val classifier = InAppClassifier(listOf("io.appwin"))
    assertFalse(classifier.isInApp("io.appwin.core.AppwinCore"))
    assertFalse(classifier.isInApp("io.appwin.analytics.AppwinAnalytics"))
    assertTrue(classifier.isInApp("io.appwin.sandbox.MainActivity"))
  }

  @Test
  fun `builds frames from a throwable stack`() {
    val error = IllegalStateException("boom")
    error.stackTrace = arrayOf(
      StackTraceElement("com.acme.app.Repo", "load", "Repo.kt", 31),
      StackTraceElement("java.lang.reflect.Method", "invoke", null, -2),
    )
    val frames = CrashFrames.from(error, InAppClassifier(listOf("com.acme.app")))
    assertEquals(CrashReport.Frame("com.acme.app.Repo.load", "Repo.kt", 31, "com.acme.app", true), frames[0])
    assertEquals(
      CrashReport.Frame("java.lang.reflect.Method.invoke", null, null, "java.lang.reflect", false),
      frames[1],
    )
  }

  @Test
  fun `root cause walk survives a cycle and stops at depth 10`() {
    val a = RuntimeException("a")
    val b = IllegalStateException("b", a)
    a.initCause(b)
    assertEquals(b, CrashFrames.rootCause(a))

    var deep: Throwable = RuntimeException("0")
    repeat(15) { deep = RuntimeException("${it + 1}", deep) }
    assertEquals("5", CrashFrames.rootCause(deep).message)
  }

  private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
}
