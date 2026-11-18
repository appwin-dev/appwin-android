@file:OptIn(AppwinInternalApi::class, ExperimentalCoroutinesApi::class)

package io.appwin.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppForegroundTest {
  private val owner = object : LifecycleOwner {
    override val lifecycle: Lifecycle get() = error("unused by the observer")
  }

  @Test
  fun `le lancement et chaque retour emettent foreground, chaque arret emet background`() = runTest(UnconfinedTestDispatcher()) {
    val seen = mutableListOf<String>()
    val fg = launch { AppForeground.foreground.collect { seen += "fg" } }
    val bg = launch { AppForeground.background.collect { seen += "bg" } }
    val returns = launch { AppForeground.returns.collect { seen += "return" } }
    AppForeground.observer.onStart(owner)
    AppForeground.observer.onStop(owner)
    AppForeground.observer.onStart(owner)
    assertEquals(listOf("fg", "bg", "fg", "return"), seen)
    fg.cancel(); bg.cancel(); returns.cancel()
  }

  @Test
  fun `un abonne tardif ne recoit pas le lancement deja passe`() = runTest(UnconfinedTestDispatcher()) {
    AppForeground.observer.onStart(owner)
    val seen = mutableListOf<String>()
    val fg = launch { AppForeground.foreground.collect { seen += "fg" } }
    assertEquals(emptyList<String>(), seen)
    AppForeground.observer.onStop(owner)
    AppForeground.observer.onStart(owner)
    assertEquals(listOf("fg"), seen)
    fg.cancel()
  }

  @Test
  fun `un arret suivi d un demarrage arrivent dans cet ordre sur un seul flux`() = runTest(UnconfinedTestDispatcher()) {
    AppForeground.observer.onStart(owner)
    val seen = mutableListOf<AppForeground.Transition>()
    val all = launch { AppForeground.transitions.collect { seen += it } }
    AppForeground.observer.onStop(owner)
    AppForeground.observer.onStart(owner)
    assertEquals(listOf(AppForeground.Transition.BACKGROUND, AppForeground.Transition.FOREGROUND), seen)
    all.cancel()
  }

  @Test
  fun `le lancement est a venir tant que le processus n est pas demarre`() {
    AppForeground.observer.onStop(owner)
    assertEquals(true, AppForeground.launchPending)
    AppForeground.observer.onStart(owner)
    assertEquals(false, AppForeground.launchPending)
  }
}
