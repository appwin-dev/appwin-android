package io.appwin.core

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Emits when the app comes back to the foreground, never for the launch itself. */
internal object AppForeground {
  private val _returns = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val returns: SharedFlow<Unit> = _returns.asSharedFlow()

  @Volatile
  private var installed = false

  fun install() {
    if (installed) return
    installed = true
    val register = Runnable {
      // runCatching: a unit-test host has no process lifecycle.
      runCatching {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
          object : DefaultLifecycleObserver {
            // ON_START fires right away for an already started process: only a
            // start that follows a stop is a return, the launch already has
            // its own initialize() round trip.
            private var wasStopped = false

            override fun onStart(owner: LifecycleOwner) {
              if (wasStopped) _returns.tryEmit(Unit)
              wasStopped = false
            }

            override fun onStop(owner: LifecycleOwner) {
              wasStopped = true
            }
          },
        )
      }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) register.run()
    else Handler(Looper.getMainLooper()).post(register)
  }
}
