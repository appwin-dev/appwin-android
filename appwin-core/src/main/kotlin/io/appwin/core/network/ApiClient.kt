package io.appwin.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** HTTP methods used by the SDK API. */
public enum class HttpMethod(public val value: String) {
  GET("GET"),
  POST("POST"),
  PATCH("PATCH"),
  DELETE("DELETE"),
}

/** Raw response, for the conditional cache, which must see the `304`. */
public data class RawResponse(val status: Int, val body: String)

/**
 * HTTP client shared by every product module.
 *
 * Headers come from a **function** rather than a fixed map, exactly as on iOS:
 * the identity changes over a session's life - a `login()` adds the user id, a
 * bootstrap adds the token - and modules must not have to rebuild a client for
 * that.
 *
 * `Json` is configured with `ignoreUnknownKeys`: the server can add a field to a
 * response without breaking binaries already on phones, which cannot be updated
 * on demand.
 */
public class ApiClient(
  private val baseUrl: String,
  private val headersProvider: () -> Map<String, String>,
  private val httpClient: OkHttpClient = defaultHttpClient(),
) {
  public constructor(baseUrl: String, headers: Map<String, String>) : this(
    baseUrl = baseUrl,
    headersProvider = { headers },
  )

  /** Request with a typed response body. */
  public suspend fun <T> request(
    path: String,
    method: HttpMethod,
    deserializer: KSerializer<T>,
    body: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
  ): T {
    val raw = execute(path, method, body, extraHeaders, acceptNotModified = false)
    return try {
      json.decodeFromString(deserializer, raw.body)
    } catch (error: Exception) {
      throw AppwinApiException.Decoding(error)
    }
  }

  /** Request with no usable response body (`204 No Content`). */
  public suspend fun requestVoid(
    path: String,
    method: HttpMethod,
    body: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
  ) {
    execute(path, method, body, extraHeaders, acceptNotModified = false)
  }

  /**
   * Raw request that does not treat a `304` as an error: it is a valid answer
   * meaning "your cache is current". Used by the product configuration, re-read
   * on every app open.
   */
  public suspend fun requestRaw(
    path: String,
    method: HttpMethod,
    extraHeaders: Map<String, String> = emptyMap(),
  ): RawResponse = execute(path, method, null, extraHeaders, acceptNotModified = true)

  private suspend fun execute(
    path: String,
    method: HttpMethod,
    body: String?,
    extraHeaders: Map<String, String>,
    acceptNotModified: Boolean,
  ): RawResponse = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(baseUrl + path)

    // Extra headers are applied after the canonical ones, so a module can
    // override a shared header for one call - the reading language on the
    // Community side, for instance.
    for ((key, value) in headersProvider()) builder.header(key, value)
    for ((key, value) in extraHeaders) builder.header(key, value)

    val payload = body?.toRequestBody(JSON_MEDIA_TYPE)
      ?: if (method == HttpMethod.POST || method == HttpMethod.PATCH) {
        // OkHttp requires a body on POST and PATCH. An empty object rather
        // than nothing: some endpoints validate the body first.
        "{}".toRequestBody(JSON_MEDIA_TYPE)
      } else {
        null
      }

    val response = httpClient.newCall(builder.method(method.value, payload).build()).await()

    response.use {
      val text = it.body?.string().orEmpty()
      val ok = it.isSuccessful || (acceptNotModified && it.code == 304)
      if (!ok) throw AppwinApiException.Http(it.code, text.ifEmpty { null })
      RawResponse(it.code, text)
    }
  }

  public companion object {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    public val json: Json = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
      encodeDefaults = true
    }

    public fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
  }
}

/**
 * Bridges the OkHttp callback to a coroutine.
 *
 * `suspendCancellableCoroutine` rather than a blocking `execute()`: cancelling
 * the coroutine - a screen closed, an app backgrounded - cancels the network
 * call instead of leaving a thread blocked on it.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
  enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) {
      continuation.resume(response)
    }

    override fun onFailure(call: Call, e: IOException) {
      if (continuation.isCancelled) return
      continuation.resumeWithException(AppwinApiException.Network(e))
    }
  })
  continuation.invokeOnCancellation {
    try {
      cancel()
    } catch (_: Throwable) {
      // Cancellation is best effort: there is nothing to recover here.
    }
  }
}
