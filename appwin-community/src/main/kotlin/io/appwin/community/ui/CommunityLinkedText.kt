package io.appwin.community.ui

import android.util.Patterns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * Text of a post or comment with its web links styled and tappable, as iOS
 * `CommunityLinkedText`.
 *
 * Members paste bare urls (« https://… », « www.… », « site.com »): the
 * platform's url pattern finds them, and a [LinkAnnotation.Url] opens them
 * through the app's `LocalUriHandler`, inside `Text` itself, so a tap on a
 * link does not also open the post. An address with no scheme opens as https.
 */
internal fun communityLinkedText(text: String, linkColor: Color): AnnotatedString {
  if (text.none { it == '.' || it == ':' }) return AnnotatedString(text)
  val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
  return buildAnnotatedString {
    var cursor = 0
    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
      val start = matcher.start()
      var end = matcher.end()
      // The domain of an email address is not a link a member meant to share.
      if (start > 0 && text[start - 1] == '@') continue
      // A sentence's closing punctuation is not part of the url.
      while (end > start && text[end - 1] in ".,;:!?)") end--
      val raw = text.substring(start, end)
      if (!raw.contains('.')) continue
      val url = if (raw.contains("://")) raw else "https://$raw"
      if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        continue
      }
      append(text.substring(cursor, start))
      withLink(LinkAnnotation.Url(url, style)) { append(raw) }
      cursor = end
    }
    append(text.substring(cursor))
  }
}
