package io.appwin.core.availability

import android.content.Context
import android.util.Log
import io.appwin.core.network.ApiClient
import io.appwin.core.network.HttpMethod
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A product module, as the server names it in `/sdk/v1/availability`. */
public enum class AppwinProduct(public val key: String) {
  SUPPORT("support"),
  COMMUNITY("community"),
  NOTIFICATIONS("notifications"),
}

/** Why a product is closed to this app. */
public enum class AppwinUnavailableReason(public val key: String) {
  /**
   * The organisation's plan does not include this product. The studio cannot
   * fix this from the dashboard: it is a sales conversation.
   */
  PLAN("plan"),

  /**
   * The product is switched off for this project. The studio turns it back on
   * from the dashboard, without shipping an app update.
   */
  DISABLED("disabled"),
}

/** Coarse outcome of a product's `initialize()`. */
public enum class AppwinInitStatus {
  /** Ready to present. The only value that unlocks the product's UI. */
  READY,

  /** The server answered, and the answer is no. See [AppwinInitResult.reason]. */
  UNAVAILABLE,

  /** [io.appwin.core.AppwinCore.configure] was never called. */
  NOT_CONFIGURED,

  /**
   * No verdict could be obtained and nothing was cached from a previous launch.
   * No network is one cause; an API too old to serve the endpoint is another.
   * Retry later; this is not a permanent no.
   */
  UNKNOWN,
}

/**
 * Outcome of a product's `initialize()`.
 *
 * A value rather than a thrown exception, deliberately. "Not entitled" is an
 * expected outcome of a normal launch, not an error: forcing callers into a
 * try/catch for the ordinary case pushes them towards swallowing it.
 *
 * A data class rather than a sealed hierarchy, and not only for taste: Dokka
 * bundles an ASM too old to read `PermittedSubclasses`, so a sealed type here
 * fails the javadoc jar that Maven Central requires. It also lines the three
 * platforms up on the same `{status, reason}` shape.
 */
public data class AppwinInitResult(
  public val status: AppwinInitStatus,
  /** Set only when [status] is [AppwinInitStatus.UNAVAILABLE]. */
  public val reason: AppwinUnavailableReason? = null,
) {
  /** The one check a host app needs before showing a product's entry point. */
  public val isReady: Boolean
    get() = status == AppwinInitStatus.READY

  public companion object {
    public val Ready: AppwinInitResult = AppwinInitResult(AppwinInitStatus.READY)
    public val NotConfigured: AppwinInitResult = AppwinInitResult(AppwinInitStatus.NOT_CONFIGURED)
    public val Unknown: AppwinInitResult = AppwinInitResult(AppwinInitStatus.UNKNOWN)

    public fun unavailable(reason: AppwinUnavailableReason): AppwinInitResult =
      AppwinInitResult(AppwinInitStatus.UNAVAILABLE, reason)
  }
}

@Serializable
internal data class AvailabilityStatus(val enabled: Boolean, val reason: String? = null)

@Serializable
internal data class AvailabilityVerdict(val products: Map<String, AvailabilityStatus> = emptyMap())

/**
 * Resolves and caches what this app is allowed to open.
 *
 * One request for the three products, not one per product: an app integrating
 * Support and Community would otherwise pay two round trips at launch for an
 * answer the server computes in one go.
 *
 * ## Offline
 *
 * The verdict is cached in shared preferences and survives relaunches, so a
 * plane journey does not close a product the studio pays for. Only a first
 * launch with no network and no cache is genuinely undecided, and that answers
 * [AppwinInitResult.Unknown] rather than a false no: locking a paying user out
 * on a failed request is a worse bug than briefly allowing one that lapsed.
 */
public class AvailabilityStore(context: Context, appId: String) {
  private val prefs =
    context.applicationContext.getSharedPreferences("appwin.core.availability", Context.MODE_PRIVATE)
  private val key = "verdict.$appId"
  private val json = Json { ignoreUnknownKeys = true }
  private val mutex = Mutex()

  private var cached: AvailabilityVerdict? = null

  private fun hydrate(): AvailabilityVerdict? {
    cached?.let { return it }
    val raw = prefs.getString(key, null) ?: return null
    return runCatching { json.decodeFromString(AvailabilityVerdict.serializer(), raw) }
      .getOrNull()
      ?.also { cached = it }
  }

  private fun persist(verdict: AvailabilityVerdict) {
    cached = verdict
    runCatching {
      prefs.edit().putString(key, json.encodeToString(AvailabilityVerdict.serializer(), verdict)).apply()
    }
  }

  /**
   * Fetches the verdict, sharing one request between concurrent callers.
   *
   * Three products initialising at launch is the normal case, and without the
   * lock that is three identical requests racing each other.
   */
  private suspend fun fetch(client: ApiClient): AvailabilityVerdict? =
    mutex.withLock {
      val fresh =
        runCatching {
            client.request(
              path = "/api/sdk/v1/availability",
              method = HttpMethod.GET,
              deserializer = AvailabilityVerdict.serializer(),
            )
          }
          .getOrNull()
      if (fresh != null) {
        persist(fresh)
        fresh
      } else {
        // Network said nothing: the last known answer beats no answer.
        hydrate()
      }
    }

  public suspend fun status(product: AppwinProduct, client: ApiClient): AppwinInitResult {
    val verdict = fetch(client) ?: return AppwinInitResult.Unknown
    // A product the server does not mention is not open. Unknown keys are
    // ignored rather than fatal, so a server that grows a product does not
    // break binaries already in the wild.
    val status = verdict.products[product.key]
      ?: return AppwinInitResult.unavailable(AppwinUnavailableReason.DISABLED)
    if (status.enabled) return AppwinInitResult.Ready
    val reason =
      AppwinUnavailableReason.entries.firstOrNull { it.key == status.reason }
        ?: AppwinUnavailableReason.DISABLED
    return AppwinInitResult.unavailable(reason)
  }
}

/**
 * Logs a refused product, loudly in debug and quietly in release.
 *
 * The asymmetry is the point: in development the integrator must trip over it
 * immediately, in production a paying user must not see a crash because a plan
 * lapsed. Silence in both would produce the worst bug of the family, a button
 * that does nothing and that nobody can report.
 */
public fun reportUnavailable(product: AppwinProduct, result: AppwinInitResult) {
  val detail =
    when (result.status) {
      AppwinInitStatus.READY -> return
      AppwinInitStatus.NOT_CONFIGURED -> "AppwinCore.configure(...) has not been called."
      // Deliberately does not say "offline". A 404 from an API older than this
      // SDK lands here too, and telling a developer their online app is offline
      // sends them looking in the wrong place.
      AppwinInitStatus.UNKNOWN ->
        "the server did not answer. Check the base URL, and that the API is " +
          "recent enough to serve /sdk/v1/availability."
      AppwinInitStatus.UNAVAILABLE ->
        when (result.reason) {
          AppwinUnavailableReason.PLAN -> "the organisation's plan does not include it."
          else -> "it is switched off for this project in the dashboard."
        }
    }
  Log.w(
    "Appwin",
    "${product.key} is not available: $detail " +
      "Gate your own UI on the result of ${product.key} initialize().",
  )
}
