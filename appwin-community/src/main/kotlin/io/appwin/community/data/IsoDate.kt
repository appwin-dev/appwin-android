package io.appwin.community.data

/**
 * Parses an ISO-8601 date into epoch milliseconds.
 *
 * Hand-written rather than delegated to `java.time`: `Instant.parse` needs API
 * 26 and the SDK targets API 24. Desugaring would fix it but would impose itself
 * on every integrating app, to parse a string of known shape.
 *
 * Returns `0` on an unreadable date: a post dated to the Unix epoch shows up in
 * the wrong place, it does not wipe the page.
 */
internal object IsoDate {
  private val PATTERN = Regex(
    """(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|z|[+-]\d{2}:?\d{2})?""",
  )

  fun toMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0
    val match = PATTERN.matchEntire(raw.trim()) ?: return 0
    val (y, mo, d, h, mi, s) = match.destructured

    val days = daysFromCivil(y.toInt(), mo.toInt(), d.toInt())
    var millis = days * 86_400_000L +
      h.toInt() * 3_600_000L +
      mi.toInt() * 60_000L +
      s.toInt() * 1_000L

    match.groupValues.getOrNull(7)?.takeIf { it.isNotEmpty() }?.let { fraction ->
      millis += fraction.padEnd(3, '0').take(3).toLong()
    }

    match.groupValues.getOrNull(8)?.takeIf { it.isNotEmpty() && !it.equals("Z", true) }
      ?.let { offset ->
        val sign = if (offset.startsWith("-")) 1 else -1
        val digits = offset.drop(1).replace(":", "")
        val offsetMillis =
          digits.take(2).toLong() * 3_600_000L + digits.drop(2).toLong() * 60_000L
        millis += sign * offsetMillis
      }

    return millis
  }

  /** Epoch milliseconds as the API expects them: UTC, `2026-10-08T14:00:00.000Z`. */
  fun fromMillis(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
      .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
      .format(java.util.Date(millis))

  /** Days since 1970-01-01, using Howard Hinnant's civil algorithm. */
  private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
  }
}
