package io.appwin.community.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.appwin.core.AppwinCore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Light freshness for an open post: no live stream of comments, just a refetch
 * of the post and its comments when the realtime hub says something about it
 * changed. The REST read stays the single source of truth.
 */
internal object PostDetailRealtime {

  /** Events that can change an open post's counts or comments. */
  val events: List<String> = listOf(
    "community.comment.created",
    "community.comment.deleted",
    "community.post.updated",
  )

  /** A burst of events (a thread being answered) costs one refetch. */
  const val DEBOUNCE_MILLIS: Long = 700L

  /**
   * True when [payload] may concern [postId]: its `changes` list names it, or
   * its `resourceId` is the post (`community.post.updated`). A payload without
   * `changes` (older servers) or unreadable counts as a match: a spare refetch
   * is cheaper than a stale screen.
   */
  fun touchesPost(payload: Any?, postId: String): Boolean {
    val data = payload as? JsonObject ?: return true
    val resourceId = (data["resourceId"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (resourceId == postId) return true
    val changes = data["changes"]
    if (changes == null || changes is JsonNull) return true
    val ids = (changes as? JsonArray) ?: return true
    return ids.any { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content == postId }
  }

  /** The payloads that concern [postId], debounced to one signal per burst. */
  @OptIn(FlowPreview::class)
  fun changesOf(
    payloads: Flow<Any?>,
    postId: String,
    debounceMillis: Long = DEBOUNCE_MILLIS,
  ): Flow<Unit> =
    payloads
      .filter { touchesPost(it, postId) }
      .map { }
      .debounce(debounceMillis)
}

/**
 * While composed, calls [onChange] (debounced) whenever a realtime event
 * concerns [postId]; unsubscribes when the screen leaves. Does nothing without
 * a realtime hub: the open, resume and pull refreshes still apply.
 */
@Composable
internal fun PostDetailRealtimeEffect(postId: String, onChange: suspend () -> Unit) {
  val latestOnChange by rememberUpdatedState(onChange)
  LaunchedEffect(postId) {
    val hub = AppwinCore.realtimeHub() ?: return@LaunchedEffect
    val payloads = Channel<Any?>(Channel.UNLIMITED)
    val subscriptions = PostDetailRealtime.events.map { event ->
      hub.on(event) { payload -> payloads.trySend(payload) }
    }
    try {
      PostDetailRealtime.changesOf(payloads.receiveAsFlow(), postId).collect { latestOnChange() }
    } finally {
      subscriptions.forEach(hub::off)
      payloads.close()
    }
  }
}
