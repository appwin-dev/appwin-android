package io.appwin.core.session

import io.appwin.core.identity.DeviceInfo
import io.appwin.core.identity.IdentityStore
import io.appwin.core.identity.SecureStore
import io.appwin.core.network.ApiClient
import io.appwin.core.network.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
private data class InitSessionBody(
  val deviceId: String,
  val externalId: String? = null,
  val platform: String? = null,
  val model: String? = null,
  val os: String? = null,
  val appVersion: String? = null,
  val sdkVersion: String? = null,
)

@Serializable
private data class InitSessionResponse(
  val token: String,
  val customerSessionId: String,
  val expiresAt: String? = null,
)

/**
 * Token-bearing session, on the SDK side.
 *
 * Encapsule `POST /api/sdk/v1/auth/init`, la persistance du jeton et son
 * lifecycle. The token is then injected by the canonical headers, and the
 * product modules never touch it.
 */
internal object AuthSession {
  private const val TOKEN_KEY = "appwin.core.bearer.token"

  /**
   * Current token, read synchronously from the network thread.
   *
   * Lazy: an empty memory cache falls back to persistent storage and
   * repopulates itself. That is what lets a relaunched app resume its session
   * without waiting for a round trip.
   */
  fun currentToken(store: SecureStore?): String? {
    IdentityStore.snapshot().bearerToken?.let { return it }
    val loaded = store?.get(TOKEN_KEY) ?: return null
    IdentityStore.mutate { it.copy(bearerToken = loaded) }
    return loaded
  }

  /**
   * Creates or rotates the session, then persists the token.
   *
   * Idempotent server-side: a second call on the same (app id, device) pair
   * rotates the token and **invalidates the previous one**. That is exactly why
   * `AppwinCore` deduplicates concurrent calls rather than letting two
   * bootstraps trip over each other.
   */
  suspend fun bootstrap(
    baseUrl: String,
    appId: String,
    deviceId: String,
    externalId: String?,
    deviceInfo: DeviceInfo?,
    sdkVersion: String,
    store: SecureStore?,
  ): String {
    // A client dedicated to init: no token, since obtaining one is the point;
    // only the app id, which designates the project.
    val client = ApiClient(
      baseUrl = baseUrl,
      headers = mapOf(
        "X-Appwin-App-Id" to appId,
        "Content-Type" to "application/json",
      ),
    )

    val body = ApiClient.json.encodeToString(
      InitSessionBody.serializer(),
      InitSessionBody(
        deviceId = deviceId,
        externalId = externalId,
        platform = deviceInfo?.platform,
        model = deviceInfo?.model,
        os = deviceInfo?.osVersion,
        appVersion = deviceInfo?.appVersion,
        sdkVersion = sdkVersion,
      ),
    )

    val response = client.request(
      path = "/api/sdk/v1/auth/init",
      method = HttpMethod.POST,
      deserializer = InitSessionResponse.serializer(),
      body = body,
    )

    IdentityStore.mutate {
      it.copy(bearerToken = response.token, sessionId = response.customerSessionId)
    }
    store?.set(TOKEN_KEY, response.token)

    return response.token
  }

  /**
   * Revokes the session server-side and clears local storage.
   *
   * Error-tolerant: if the revoke fails - offline, session already dead - the
   * local token is cleared anyway. "No longer authenticated on this device"
   * matters more than server-side tidiness.
   */
  suspend fun signOut(baseUrl: String, store: SecureStore?) {
    val token = currentToken(store)
    if (token != null) {
      val client = ApiClient(baseUrl, mapOf("Authorization" to "Bearer $token"))
      runCatching {
        client.requestVoid(path = "/api/sdk/v1/auth/revoke", method = HttpMethod.POST)
      }
    }
    clearLocal(store)
  }

  fun clearLocal(store: SecureStore?) {
    IdentityStore.mutate { it.copy(bearerToken = null, sessionId = null) }
    store?.delete(TOKEN_KEY)
  }
}
