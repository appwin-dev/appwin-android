package io.appwin.core.session

import io.appwin.core.AppwinUserAttributes
import io.appwin.core.identity.IdentityStore
import io.appwin.core.identity.SecureStore
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import io.appwin.core.network.RealtimeHub
import io.appwin.core.push.PushTokenBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Who the device's session belongs to: `identify`, `updateUser`, `logout`
 * and the push token they must carry over. `AppwinCore` only forwards to it.
 *
 * Dependencies are providers because `configure` can run (again) after this
 * object exists.
 */
internal class IdentityManager(
  scope: CoroutineScope,
  private val store: () -> SecureStore?,
  private val client: () -> ApiClient?,
  private val baseUrl: () -> String,
  private val realtimeHub: () -> RealtimeHub?,
  openSession: suspend (externalId: String?) -> String,
) {
  private val sessions = SessionCoordinator(scope, openSession)

  /** Replayed after [logout]: the server drops the device's push tokens on revoke. */
  @Volatile
  private var lastPushToken: PushTokenBody? = null

  @Volatile
  var hasRegisteredPushToken: Boolean = false
    private set

  private val changesFlow = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  val changes: SharedFlow<Unit> = changesFlow.asSharedFlow()

  fun storedExternalId(store: SecureStore): String? = store.get(EXTERNAL_ID_KEY)

  suspend fun bootstrap(): String = sessions.bootstrap(IdentityStore.snapshot().externalId)

  suspend fun identify(externalId: String, attributes: AppwinUserAttributes?) {
    require(externalId.isNotBlank()) { "externalId must not be blank" }
    val store = store() ?: throw AppwinApiException.NotConfigured()
    store.set(EXTERNAL_ID_KEY, externalId)
    IdentityStore.mutate { it.copy(externalId = externalId) }
    sessions.bootstrap(externalId)
    realtimeHub()?.reconnect()
    try {
      attributes?.let { patchUser(it) }
    } finally {
      changesFlow.tryEmit(Unit)
    }
  }

  suspend fun updateUser(attributes: AppwinUserAttributes) {
    if (AuthSession.currentToken(store()) == null) bootstrap()
    patchUser(attributes)
    changesFlow.tryEmit(Unit)
  }

  suspend fun logout() {
    AuthSession.signOut(baseUrl(), store())
    store()?.delete(EXTERNAL_ID_KEY)
    IdentityStore.mutate { it.copy(externalId = null) }
    runCatching { sessions.bootstrap(null) }
      .onSuccess {
        lastPushToken?.let { token -> runCatching { postPushToken(token) } }
      }
    realtimeHub()?.reconnect()
    changesFlow.tryEmit(Unit)
  }

  suspend fun registerPushToken(body: PushTokenBody) {
    postPushToken(body)
    lastPushToken = body
  }

  fun reset() {
    sessions.reset()
    lastPushToken = null
    hasRegisteredPushToken = false
  }

  private suspend fun patchUser(attributes: AppwinUserAttributes) {
    val api = client() ?: throw AppwinApiException.NotConfigured()
    api.requestVoid(
      path = "/api/sdk/v1/me",
      method = HttpMethod.PATCH,
      body = ApiClient.json.encodeToString(AppwinUserAttributes.serializer(), attributes),
    )
  }

  private suspend fun postPushToken(body: PushTokenBody) {
    val api = client() ?: throw AppwinApiException.NotConfigured()
    api.requestVoid(
      path = "/api/sdk/support/v1/push-token",
      method = HttpMethod.POST,
      body = ApiClient.json.encodeToString(PushTokenBody.serializer(), body),
    )
    hasRegisteredPushToken = true
  }

  private companion object {
    const val EXTERNAL_ID_KEY = "appwin.core.externalId"
  }
}
