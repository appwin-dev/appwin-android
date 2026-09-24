package io.appwin.core.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCoordinatorTest {
  @Test
  fun `callers asking for the same identity share one call`() = runTest {
    val gate = CompletableDeferred<Unit>()
    val calls = mutableListOf<String?>()
    val sessions = SessionCoordinator(supervised()) { externalId ->
      calls += externalId
      gate.await()
      "tok-$externalId"
    }

    val first = async(start = CoroutineStart.UNDISPATCHED) { sessions.bootstrap("u1") }
    val second = async(start = CoroutineStart.UNDISPATCHED) { sessions.bootstrap("u1") }
    gate.complete(Unit)

    assertEquals("tok-u1", first.await())
    assertEquals("tok-u1", second.await())
    assertEquals(listOf<String?>("u1"), calls)
  }

  @Test
  fun `an identify racing an anonymous bootstrap gets its own session`() = runTest {
    val gate = CompletableDeferred<Unit>()
    val calls = mutableListOf<String?>()
    val sessions = SessionCoordinator(supervised()) { externalId ->
      calls += externalId
      gate.await()
      "tok-$externalId"
    }

    val anonymous = async(start = CoroutineStart.UNDISPATCHED) { sessions.bootstrap(null) }
    val identified = async(start = CoroutineStart.UNDISPATCHED) { sessions.bootstrap("u1") }
    gate.complete(Unit)

    assertEquals("tok-null", anonymous.await())
    assertEquals("tok-u1", identified.await())
    assertEquals(listOf(null, "u1"), calls)
  }

  @Test
  fun `a failed call of another identity does not fail the caller`() = runTest {
    val gate = CompletableDeferred<Unit>()
    val sessions = SessionCoordinator(supervised()) { externalId ->
      gate.await()
      if (externalId == null) error("offline") else "tok-$externalId"
    }

    val anonymous = async(start = CoroutineStart.UNDISPATCHED) {
      runCatching { sessions.bootstrap(null) }
    }
    val identified = async(start = CoroutineStart.UNDISPATCHED) { sessions.bootstrap("u1") }
    gate.complete(Unit)

    assertEquals(true, anonymous.await().isFailure)
    assertEquals("tok-u1", identified.await())
  }

  // Mirrors Core's SupervisorJob scope: a failed session call must not
  // cancel the test itself.
  private fun TestScope.supervised() = CoroutineScope(coroutineContext + SupervisorJob())
}
