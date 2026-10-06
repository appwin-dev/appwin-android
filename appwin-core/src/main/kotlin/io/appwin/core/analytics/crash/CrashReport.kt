package io.appwin.core.analytics.crash

import io.appwin.core.analytics.IsoDate
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * One crash report, shaped after `CrashReportSchema` in `@app-win/contracts`
 * (ADR-0056). The bounds are applied here, at capture: a report the server
 * would reject is a report lost for good.
 */
internal data class CrashReport(
  val crashId: String,
  val kind: Kind,
  val occurredAtMs: Long,
  val sessionId: String?,
  val screen: String?,
  val exceptionType: String,
  val exceptionMessage: String?,
  val frames: List<Frame>,
  val context: CrashContext,
  val breadcrumbs: List<Breadcrumb>,
  val runtime: Runtime = Runtime.ANDROID,
) {
  enum class Kind(val wire: String) { CRASH("crash"), NON_FATAL("non_fatal"), ANR("anr") }

  /** The code that produced the stack, not the OS (ADR-0056). */
  enum class Runtime(val wire: String) {
    ANDROID("android"), FLUTTER("flutter"), REACT_NATIVE("react_native");

    companion object {
      /** Only the cross-platform plugins bridge errors in; `android` is ours to capture. */
      fun bridged(wire: String): Runtime? = when (wire) {
        FLUTTER.wire -> FLUTTER
        REACT_NATIVE.wire -> REACT_NATIVE
        else -> null
      }
    }
  }

  data class Frame(
    val fn: String,
    val file: String?,
    val line: Int?,
    val module: String?,
    val inApp: Boolean,
    val col: Int? = null,
  )

  data class Breadcrumb(val atMs: Long, val type: Type, val name: String) {
    enum class Type(val wire: String) { SCREEN("screen"), EVENT("event") }
  }

  fun toJson(): JsonObject = buildJsonObject {
    put("crashId", crashId)
    put("kind", kind.wire)
    put("runtime", runtime.wire)
    put("occurredAt", IsoDate.format(occurredAtMs))
    sessionId?.let { put("sessionId", it) }
    screen?.let { put("screen", it.take(MAX_SCREEN)) }
    putJsonObject("exception") {
      put("type", exceptionType.ifEmpty { "Unknown" }.take(MAX_TYPE))
      exceptionMessage?.let { put("message", it.take(MAX_MESSAGE)) }
    }
    putJsonArray("frames") {
      for (frame in frames.take(MAX_FRAMES)) {
        addJsonObject {
          put("fn", frame.fn.take(MAX_FN))
          frame.file?.let { put("file", it.take(MAX_FN)) }
          frame.line?.takeIf { it >= 0 }?.let { put("line", it) }
          frame.col?.takeIf { it >= 0 }?.let { put("col", it) }
          frame.module?.let { put("module", it.take(MAX_MODULE)) }
          put("inApp", frame.inApp)
        }
      }
    }
    putJsonObject("app") {
      put("version", context.appVersion.take(MAX_VERSION))
      context.appBuild?.let { put("build", it.take(MAX_VERSION)) }
    }
    putJsonObject("device") {
      put("os", context.os.take(MAX_VERSION))
      put("model", context.model.take(MAX_SCREEN))
    }
    put("sdkVersion", context.sdkVersion.take(MAX_SDK_VERSION))
    if (breadcrumbs.isNotEmpty()) {
      putJsonArray("breadcrumbs") {
        for (crumb in breadcrumbs.takeLast(Breadcrumbs.CAPACITY)) {
          addJsonObject {
            put("at", IsoDate.format(crumb.atMs))
            put("type", crumb.type.wire)
            put("name", crumb.name.take(MAX_SCREEN))
          }
        }
      }
    }
  }

  companion object {
    const val MAX_FRAMES = 200
    const val MAX_MESSAGE = 1024
    const val MAX_TYPE = 256
    const val MAX_FN = 512
    const val MAX_MODULE = 256
    private const val MAX_SCREEN = 128
    private const val MAX_VERSION = 64
    private const val MAX_SDK_VERSION = 32
  }
}

/**
 * App and device, frozen when the crash happens: the report leaves on the
 * next launch, possibly after an update (ADR-0056).
 */
internal data class CrashContext(
  val appVersion: String,
  val appBuild: String?,
  val os: String,
  val model: String,
  val sdkVersion: String,
)

/**
 * Decides which frames are the studio's own code, the only ones feeding the
 * issue fingerprint server-side.
 *
 * Package prefixes rather than "everything but the platform": an unknown
 * third-party library classified in-app would split issues by library
 * version. The SDK modules are excluded one by one, not as `io.appwin.*`,
 * so an app living under that namespace (our own sandboxes) still counts.
 */
internal class InAppClassifier(inAppPrefixes: Collection<String>) {
  private val prefixes = inAppPrefixes.filter { it.isNotBlank() }.map { it.trimEnd('.') + "." }

  fun isInApp(className: String): Boolean =
    EXCLUDED.none { className.startsWith(it) } && prefixes.any { className.startsWith(it) }

  private companion object {
    val EXCLUDED = listOf(
      "io.appwin.core.", "io.appwin.analytics.", "io.appwin.attribution.",
      "io.appwin.support.", "io.appwin.community.", "io.appwin.notifications.",
      "io.appwin.tiktok.", "io.appwin.flutter.", "io.appwin.reactnative.",
      "android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.",
      "com.android.", "dalvik.", "libcore.", "sun.",
    )
  }
}

internal object CrashFrames {
  private const val MAX_CAUSE_DEPTH = 10

  /** [throwable], then its causes. Cycle-safe: `initCause` allows loops. */
  fun causeChain(throwable: Throwable): Sequence<Throwable> {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    return generateSequence(throwable) { it.cause }.takeWhile { seen.add(it) }
  }

  /**
   * Innermost cause, which the report describes. Wrappers ("Unable to start
   * activity", InvocationTargetException) would otherwise fold unrelated
   * bugs into one issue.
   */
  fun rootCause(throwable: Throwable): Throwable =
    causeChain(throwable).take(MAX_CAUSE_DEPTH + 1).last()

  fun from(throwable: Throwable, classifier: InAppClassifier): List<CrashReport.Frame> =
    throwable.stackTrace.asSequence().take(CrashReport.MAX_FRAMES).map {
      frame(it.className, it.methodName, it.fileName, it.lineNumber, classifier)
    }.toList()

  fun frame(
    className: String,
    methodName: String,
    fileName: String?,
    lineNumber: Int?,
    classifier: InAppClassifier,
  ): CrashReport.Frame = CrashReport.Frame(
    fn = "$className.$methodName",
    file = fileName,
    // Negative line numbers mean "unknown" (-1) or "native method" (-2).
    line = lineNumber?.takeIf { it >= 0 },
    module = className.substringBeforeLast('.', "").ifEmpty { null },
    inApp = classifier.isInApp(className),
  )
}

/**
 * Frames handed over by the Flutter and React Native plugins, as maps decoded
 * from a method channel or a `ReadableArray`. Those decoders pick the number
 * type themselves (Int, Long or Double for the same value), hence the
 * lenient coercion; anything unusable is dropped rather than failing the
 * whole report.
 */
internal object BridgedFrames {
  fun from(frames: List<Map<String, Any?>>): List<CrashReport.Frame> =
    frames.asSequence().take(CrashReport.MAX_FRAMES).map { frame(it) }.toList()

  private fun frame(map: Map<String, Any?>): CrashReport.Frame = CrashReport.Frame(
    fn = (map["fn"] as? String).orEmpty().take(CrashReport.MAX_FN),
    file = (map["file"] as? String)?.take(CrashReport.MAX_FN),
    line = position(map["line"]),
    module = (map["module"] as? String)?.take(CrashReport.MAX_MODULE),
    inApp = map["inApp"] as? Boolean ?: false,
    col = position(map["col"]),
  )

  private fun position(value: Any?): Int? {
    val number = value as? Number ?: return null
    val double = number.toDouble()
    if (double.isNaN() || double < 0 || double > Int.MAX_VALUE) return null
    return number.toInt()
  }
}
