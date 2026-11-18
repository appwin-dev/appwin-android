package io.appwin.core

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * The one observer of the process lifecycle in the SDK (ADR-0061): every
 * product subscribes here instead of watching [ProcessLifecycleOwner]
 * itself. ProcessLifecycleOwner only stops when the whole app leaves the
 * screen (a dialog, the notification shade or a rotation do not stop it), so
 * [foreground] fires for the launch and for a return after a real background.
 * No replay: a subscriber arriving after the launch does not hear it.
 */
@AppwinInternalApi
public object AppForeground {
  /** One step of the process lifecycle, in the order the system delivered it. */
  public enum class Transition { FOREGROUND, BACKGROUND }

  private val _transitions = MutableSharedFlow<Transition>(extraBufferCapacity = 16)

  /**
   * Every start (the launch included) and stop, on one ordered stream: a
   * consumer that cares about the order (the event pipeline) collects this
   * with a single coroutine, never [foreground] and [background] apart.
   */
  public val transitions: SharedFlow<Transition> = _transitions.asSharedFlow()

  /** Emits at each start of the process, the launch included. */
  public val foreground: Flow<Unit> =
    _transitions.filter { it == Transition.FOREGROUND }.map { }

  /** Emits at each stop of the process. */
  public val background: Flow<Unit> =
    _transitions.filter { it == Transition.BACKGROUND }.map { }

  private val _returns = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /** Emits when the app comes back to the foreground, never for the launch itself. */
  public val returns: SharedFlow<Unit> = _returns.asSharedFlow()

  private val _inForeground = MutableStateFlow(false)

  /** Process-wide visibility, debounced by ProcessLifecycleOwner across rotations. */
  public val inForeground: StateFlow<Boolean> = _inForeground.asStateFlow()

  /**
   * Whether the process start is still to come: true until the first
   * ON_START, so a product started early lets the launch foreground do its
   * work instead of doing it twice.
   */
  public val launchPending: Boolean
    get() = !_inForeground.value

  // ON_START fires right away for an already started process: only a start
  // that follows a stop is a return.
  internal val observer: DefaultLifecycleObserver = object : DefaultLifecycleObserver {
    private var wasStopped = false

    override fun onStart(owner: LifecycleOwner) {
      _inForeground.value = true
      _transitions.tryEmit(Transition.FOREGROUND)
      if (wasStopped) _returns.tryEmit(Unit)
      wasStopped = false
    }

    override fun onStop(owner: LifecycleOwner) {
      _inForeground.value = false
      _transitions.tryEmit(Transition.BACKGROUND)
      wasStopped = true
    }
  }

  @Volatile
  private var installed = false

  internal fun install() {
    if (installed) return
    installed = true
    val register = Runnable {
      // runCatching: a unit-test host has no process lifecycle.
      runCatching { ProcessLifecycleOwner.get().lifecycle.addObserver(observer) }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) register.run()
    else Handler(Looper.getMainLooper()).post(register)
  }
}
