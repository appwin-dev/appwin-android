package io.appwin.core.analytics

import android.util.Log
import io.appwin.core.consent.ConsentPurpose
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hands each event to the parts of the SDK that ride on the stream (ad
 * adapters, crash breadcrumbs). Internal: not open to studios.
 *
 * A subscriber names the [ConsentPurpose] it needs; [isGranted] is asked at
 * every publish, so an answer given a moment ago applies to the next event.
 */
internal class EventBus(private val isGranted: (ConsentPurpose) -> Boolean) {
  data class Event(val name: String, val eventId: String, val props: Map<String, Any?>?)

  private class Subscription(val purpose: ConsentPurpose?, val handler: (Event) -> Unit)

  private val subscriptions = CopyOnWriteArrayList<Subscription>()

  /** `purpose == null`: delivered whatever the consent. Returns a handle; `close()` unsubscribes. */
  fun subscribe(purpose: ConsentPurpose?, handler: (Event) -> Unit): AutoCloseable {
    val subscription = Subscription(purpose, handler)
    subscriptions.add(subscription)
    return AutoCloseable { subscriptions.remove(subscription) }
  }

  fun publish(event: Event) {
    for (subscription in subscriptions) {
      val purpose = subscription.purpose
      if (purpose != null && !isGranted(purpose)) continue
      // One failing subscriber must not starve the others, or the caller.
      runCatching { subscription.handler(event) }
        .onFailure { Log.w("Appwin", "event bus: a subscriber failed", it) }
    }
  }
}
