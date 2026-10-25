package io.appwin.analytics

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

internal val AppwinMaskKey = SemanticsPropertyKey<Boolean>(
  name = "AppwinReplayMask",
  mergePolicy = { parent, _ -> parent },
)

/**
 * Hides this composable and its content in session replays: an opaque
 * rectangle is painted over it on the device, before encoding.
 *
 * ```kotlin
 * Text(card.number, Modifier.appwinMask())
 * ```
 */
public fun Modifier.appwinMask(): Modifier = semantics { this[AppwinMaskKey] = true }

/**
 * Shows this composable and its content in session replays even when the
 * project masks all text or all images. Text fields stay masked regardless.
 */
public fun Modifier.appwinUnmask(): Modifier = semantics { this[AppwinMaskKey] = false }
