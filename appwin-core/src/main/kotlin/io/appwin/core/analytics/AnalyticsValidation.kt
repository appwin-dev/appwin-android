package io.appwin.core.analytics

/**
 * Client-side validation of event names and props, mirroring the server
 * contract. Failing early matters: the server counts an invalid event in
 * `rejected` and moves on, which an integrator would never see.
 */
internal object AnalyticsValidation {
  /** Emitted by the SDK itself; a custom event must not shadow them. */
  val RESERVED_NAMES: Set<String> =
    setOf(
      "session_start", "session_end", "screen_view", "app_install", "app_update",
      "install_referrer",
    )

  const val MAX_PROPS_COUNT = 20
  const val MAX_PROP_KEY_LENGTH = 64
  const val MAX_PROP_STRING_LENGTH = 256
  const val MAX_SCREEN_LENGTH = 128

  /** Server rule: `^[a-z][a-z0-9_]{0,63}$`, spelled out to stay allocation-free. */
  fun isValidEventName(name: String): Boolean {
    if (name.length !in 1..64 || name[0] !in 'a'..'z') return false
    return name.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
  }

  /**
   * Drops non-scalar values and oversized keys, truncates long strings, caps
   * the count at 20 (alphabetical order, so which keys survive is
   * deterministic).
   */
  fun sanitizeProps(props: Map<String, Any?>?): Map<String, Any?>? {
    if (props.isNullOrEmpty()) return null
    val out = LinkedHashMap<String, Any?>(minOf(props.size, MAX_PROPS_COUNT))
    for (key in props.keys.sorted()) {
      if (out.size >= MAX_PROPS_COUNT) break
      if (key.isEmpty() || key.length > MAX_PROP_KEY_LENGTH) continue
      when (val value = props[key]) {
        is String -> out[key] = value.take(MAX_PROP_STRING_LENGTH)
        is Boolean, is Int, is Long, is Float, is Double -> out[key] = value
        else -> {}
      }
    }
    return out.ifEmpty { null }
  }
}
