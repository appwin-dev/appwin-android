package io.appwin.core.push

import android.net.Uri
import io.appwin.core.AppwinInternalApi
import io.appwin.core.network.ApiClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * An Appwin push, parsed once for every product.
 *
 * Wire contract: `packages/contracts/src/sdk-identity/sdk-push.ts`.
 */
@AppwinInternalApi
public data class AppwinPushPayload(
  /** `appwinType`, or null on a legacy push sent before the contract. */
  public val type: String?,
  /** Product that owns the push: `support`, `notifications`, ... */
  public val product: String,
  public val deeplink: String?,
  public val deliveryId: String?,
  public val imageUrl: String?,
  public val title: String?,
  public val body: String?,
  /** Every key received, Appwin's and the host's. */
  public val raw: Map<String, String>,
) {
  /** Nothing to display: the push only tells the SDK to fetch something. */
  public val isSilent: Boolean get() = type == TYPE_INAPP_PENDING

  /** The product [deeplink] routes to, when it is an internal `appwin://` route. */
  public val deeplinkProduct: String? get() = deeplink?.let(::routeOf)?.product

  public companion object {
    public const val KEY_TYPE: String = "appwinType"
    public const val KEY_VERSION: String = "appwinVersion"
    public const val KEY_DEEPLINK: String = "deeplink"
    public const val KEY_DELIVERY_ID: String = "deliveryId"
    public const val KEY_IMAGE_URL: String = "imageUrl"

    public const val TYPE_INAPP_PENDING: String = "inapp.pending"
    public const val SCHEME: String = "appwin"

    internal val APPWIN_KEYS =
      listOf(KEY_TYPE, KEY_VERSION, KEY_DEEPLINK, KEY_DELIVERY_ID, KEY_IMAGE_URL)

    /** Null when [data] is not an Appwin push. */
    @JvmStatic
    public fun parse(
      data: Map<String, String>,
      title: String? = null,
      body: String? = null,
    ): AppwinPushPayload? {
      val flat = flatten(data)
      val type = flat[KEY_TYPE].nonBlank()
      val deeplink = flat[KEY_DEEPLINK].nonBlank()
      val deliveryId = flat[KEY_DELIVERY_ID].nonBlank()
      val product = productOf(type, deeplink, deliveryId) ?: return null
      return AppwinPushPayload(
        type = type,
        product = product,
        deeplink = deeplink,
        deliveryId = deliveryId,
        imageUrl = flat[KEY_IMAGE_URL].nonBlank(),
        title = title.nonBlank() ?: flat["title"].nonBlank(),
        body = body.nonBlank() ?: flat["body"].nonBlank(),
        raw = flat,
      )
    }

    /**
     * Before the contract a push carried no `appwinType`: an `appwin://`
     * deeplink or a `deliveryId` is what still marks those as Appwin's.
     */
    internal fun productOf(type: String?, deeplink: String?, deliveryId: String?): String? {
      if (type != null) {
        if (type == TYPE_INAPP_PENDING) return "notifications"
        return type.substringBefore('.').takeIf { it.isNotEmpty() }
      }
      deeplink?.let(::routeOf)?.let { return it.product }
      if (deliveryId != null) return "notifications"
      return null
    }

    /**
     * `appwin://<product>/<path...>`, or `appwin:///<product>/<path...>` as some
     * older payloads were written. Null for any other URL.
     */
    @JvmStatic
    public fun routeOf(url: String): AppwinRoute? {
      val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
      if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
      val segments = uri.pathSegments.orEmpty().filter { it.isNotEmpty() }
      val host = uri.host?.lowercase().nonBlank()
      return when {
        host != null -> AppwinRoute(host, segments)
        segments.isNotEmpty() -> AppwinRoute(segments.first().lowercase(), segments.drop(1))
        else -> null
      }
    }

    /**
     * Some senders nest the keys under a `data` JSON string. Top-level keys win:
     * they are what the current server writes.
     */
    private fun flatten(data: Map<String, String>): Map<String, String> {
      val nested = data["data"]?.takeIf { it.trimStart().startsWith("{") } ?: return data
      val obj = runCatching { ApiClient.json.parseToJsonElement(nested) as? JsonObject }
        .getOrNull() ?: return data
      val merged = LinkedHashMap<String, String>()
      for ((key, value) in obj) {
        (value as? JsonPrimitive)?.takeIf { it.isString }?.let { merged[key] = it.content }
      }
      merged.putAll(data)
      return merged
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
  }
}

/** An internal `appwin://` route: its product, and the path below it. */
@AppwinInternalApi
public data class AppwinRoute(
  public val product: String,
  public val segments: List<String>,
)
