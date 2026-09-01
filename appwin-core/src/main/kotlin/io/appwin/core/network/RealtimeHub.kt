package io.appwin.core.network

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Multiplexed realtime hub (ADR-0028 §9): one WebSocket connection per app,
 * shared by every product SDK.
 */
public class RealtimeHub private constructor(
  private val gatewayUrl: String,
  private val mintToken: suspend () -> String?,
) {
  private val lock = Any()
  private val handlers = mutableMapOf<String, MutableList<Pair<UUID, (Any?) -> Unit>>>()
  private val connectedCallbacks = mutableListOf<Pair<UUID, () -> Unit>>()
  private var topics: List<String> = emptyList()
  private var started = false
  private var connectedNotified = false
  private var backoffAttempt = 0

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var connectJob: Job? = null
  private var reconnectJob: Job? = null
  private var webSocket: WebSocket? = null

  private val wsClient: OkHttpClient = OkHttpClient.Builder()
    .pingInterval(25, TimeUnit.SECONDS)
    .build()

  public companion object {
    private val tokenJson = Json { ignoreUnknownKeys = true }

    /** Builds a hub wired to the SDK API, minting through the canonical client. */
    public fun make(gatewayUrl: String, api: ApiClient): RealtimeHub =
      RealtimeHub(gatewayUrl) {
        val response: TokenResponse? = runCatching {
          api.request(
            path = "/api/sdk/v1/realtime/token",
            method = HttpMethod.POST,
            deserializer = TokenResponse.serializer(),
          )
        }.getOrNull()
        response?.token
      }
  }

  /** Subscribes to a domain event. Returns an id for [off]. */
  public fun on(event: String, callback: (Any?) -> Unit): UUID {
    val id = UUID.randomUUID()
    synchronized(lock) {
      handlers.getOrPut(event) { mutableListOf() }.add(id to callback)
    }
    ensureStarted()
    return id
  }

  public fun off(id: UUID) {
    synchronized(lock) {
      handlers.keys.toList().forEach { event ->
        handlers[event] = handlers[event]?.filterNot { it.first == id }?.toMutableList() ?: mutableListOf()
      }
      connectedCallbacks.removeAll { it.first == id }
    }
  }

  /** (Re)connection callback for REST resync (at-most-once semantics). */
  public fun onConnected(callback: () -> Unit): UUID {
    val id = UUID.randomUUID()
    synchronized(lock) {
      connectedCallbacks.add(id to callback)
    }
    ensureStarted()
    return id
  }

  public fun start() {
    ensureStarted()
  }

  public fun stop() {
    synchronized(lock) {
      started = false
      connectedNotified = false
      backoffAttempt = 0
    }
    connectJob?.cancel()
    reconnectJob?.cancel()
    webSocket?.close(1000, "client stop")
    webSocket = null
  }

  private fun ensureStarted() {
    synchronized(lock) {
      if (started) return
      started = true
    }
    connect()
  }

  private fun connect() {
    connectJob?.cancel()
    connectJob = scope.launch {
      val token = mintToken()
      if (token.isNullOrBlank()) {
        scheduleReconnect()
        return@launch
      }
      rememberTopics(token)
      val request = Request.Builder()
        .url(gatewayUrl)
        .header("Authorization", "Bearer $token")
        .build()
      webSocket = wsClient.newWebSocket(request, socketListener)
    }
  }

  private fun rememberTopics(token: String) {
    topics = decodeJwtTopics(token)
  }

  private fun subscribeAll() {
    val socket = webSocket ?: return
    val list = synchronized(lock) { topics.toList() }
    list.forEach { topic ->
      socket.send("""{"a":"sub","topic":"$topic"}""")
    }
  }

  private fun handleMessage(text: String) {
    val root = runCatching { tokenJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return

    if (root.containsKey("a")) {
      if (root["a"]?.jsonPrimitive?.content == "sub:ok") {
        notifyConnectedOnce()
      }
      return
    }

    val eventName = root["t"]?.jsonPrimitive?.content ?: return
    val payload = root["data"]
    val callbacks = synchronized(lock) {
      handlers[eventName]?.map { it.second } ?: emptyList()
    }
    callbacks.forEach { callback ->
      runCatching { callback(payload) }
    }
  }

  private fun notifyConnectedOnce() {
    val callbacks = synchronized(lock) {
      if (connectedNotified) return
      connectedNotified = true
      backoffAttempt = 0
      connectedCallbacks.map { it.second }
    }
    callbacks.forEach { callback ->
      runCatching { callback() }
    }
  }

  private fun scheduleReconnect() {
    synchronized(lock) {
      if (!started) return
      connectedNotified = false
    }
    reconnectJob?.cancel()
    reconnectJob = scope.launch {
      val attempt = synchronized(lock) {
        backoffAttempt += 1
        backoffAttempt
      }
      val delayMs = min(30_000L, 1_000L shl min(attempt, 5))
      delay(delayMs)
      if (synchronized(lock) { started }) connect()
    }
  }

  private val socketListener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      subscribeAll()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      handleMessage(text)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      synchronized(lock) { connectedNotified = false }
      scheduleReconnect()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      synchronized(lock) { connectedNotified = false }
      scheduleReconnect()
    }
  }

  @Serializable
  private data class TokenResponse(
    val token: String,
    @SerialName("expiresIn") val expiresIn: Int = 60,
  )

  @Serializable
  private data class TokenClaims(val topics: List<String> = emptyList())
}

private fun decodeJwtTopics(token: String): List<String> {
  val parts = token.split(".")
  if (parts.size != 3) return emptyList()
  var payload = parts[1].replace('-', '+').replace('_', '/')
  while (payload.length % 4 != 0) payload += "="
  val bytes = Base64.decode(payload, Base64.DEFAULT)
  return runCatching {
    RealtimeHubTokenClaims.decode(String(bytes)).topics
  }.getOrDefault(emptyList())
}

@Serializable
private data class RealtimeHubTokenClaims(val topics: List<String> = emptyList()) {
  companion object {
    private val json = Json { ignoreUnknownKeys = true }
    fun decode(payload: String): RealtimeHubTokenClaims =
      json.decodeFromString(serializer(), payload)
  }
}
