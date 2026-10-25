package io.appwin.core.identity

/**
 * Identity state injected into the HTTP headers.
 *
 * Mirror of Swift's `IdentityState`: project app id, device id, host app user
 * id, session token.
 */
internal data class IdentityState(
  val projectAppId: String? = null,
  val deviceId: String? = null,
  val externalId: String? = null,
  val bearerToken: String? = null,
  val sessionId: String? = null,
)

/**
 * Source of truth for the SDK identity, readable **synchronously from any
 * thread**.
 *
 * That constraint dictates the shape: the OkHttp interceptor builds the headers
 * on the calling thread, on every request. A suspending read (`Mutex`,
 * `StateFlow.first()`) would be impossible there, and reading from the main
 * thread would be a block.
 *
 * Hence a `@Volatile` behind a write lock: uncontended reads, serialised writes.
 */
internal object IdentityStore {
  private val writeLock = Any()

  @Volatile
  private var state: IdentityState = IdentityState()

  fun snapshot(): IdentityState = state

  fun mutate(block: (IdentityState) -> IdentityState) {
    synchronized(writeLock) {
      state = block(state)
    }
  }

  /** Resets the state. Test-only, and for a destructive reset. */
  fun reset() {
    synchronized(writeLock) {
      state = IdentityState()
    }
  }
}
