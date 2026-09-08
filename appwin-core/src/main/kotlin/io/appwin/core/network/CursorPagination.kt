package io.appwin.core.network

import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * One page's parameters - what the SDK **sends**.
 *
 * Mirror of the API's `CursorParams` and its Swift equivalent. The cursor is
 * **opaque**: the SDK never interprets it and hands it back as-is, so each
 * server-side domain stays free to change its internal strategy without breaking
 * binaries already installed.
 */
public data class CursorPageQuery(
  val cursor: String? = null,
  val limit: Int = 20,
) {
  /** Ready to concatenate onto the path, `""` when there is nothing to pass. */
  public val queryString: String
    get() {
      val parts = mutableListOf("limit=$limit")
      cursor?.let { parts += "cursor=" + URLEncoder.encode(it, "UTF-8") }
      return "?" + parts.joinToString("&")
    }
}

/**
 * Envelope of a paginated response - what the SDK **receives**.
 *
 * A `null` `nextCursor` marks the end: that is the only stop condition to test.
 * Relying on a page shorter than `limit` is wrong, since the server can filter
 * after paginating.
 */
@Serializable
public data class CursorPage<T>(
  val data: List<T> = emptyList(),
  val nextCursor: String? = null,
  val total: Int? = null,
)
