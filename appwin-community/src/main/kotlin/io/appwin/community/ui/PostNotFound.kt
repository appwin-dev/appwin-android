package io.appwin.community.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A post opened by id that this member cannot see (removed, hidden, or offline). */
@Composable
internal fun PostNotFound(strings: CommunityStrings, modifier: Modifier = Modifier, onRetry: () -> Unit) {
  CommunityEmptyState(
    icon = Icons.Default.Info,
    title = strings.loadErrorTitle,
    message = strings.loadErrorMessage,
    actionLabel = strings.retry,
    onAction = onRetry,
    modifier = modifier.fillMaxSize(),
  )
}
