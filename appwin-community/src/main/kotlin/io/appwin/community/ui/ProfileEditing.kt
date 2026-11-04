package io.appwin.community.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.staticCompositionLocalOf

/** The profile fields a host app edits itself, as passed to `CommunityView` or `presentCommunity`. */
internal class HostProfileHandlers(
  val profile: (() -> Unit)?,
  val photo: (() -> Unit)?,
  val name: (() -> Unit)?,
)

/**
 * What the host declared about profile editing, beyond the lifetime of the view
 * or presentation that declared it.
 */
internal object HostProfileEditing {
  private val main = Handler(Looper.getMainLooper())
  private val presentations = mutableMapOf<Long, HostProfileHandlers>()
  private var nextPresentation = 0L

  // Sticky on purpose: a post opened from a notification, with no view mounted,
  // must still not let the member edit a field the app declared as its own.
  @Volatile var ownsProfile: Boolean = false
    private set

  @Volatile var ownsPhoto: Boolean = false
    private set

  @Volatile var ownsName: Boolean = false
    private set

  fun declare(handlers: HostProfileHandlers) {
    ownsProfile = handlers.profile != null
    ownsPhoto = handlers.photo != null
    ownsName = handlers.name != null
  }

  /** Keeps [handlers] for the activity about to be started; its id goes in the intent. */
  fun present(handlers: HostProfileHandlers): Long {
    declare(handlers)
    return synchronized(presentations) {
      val id = ++nextPresentation
      presentations[id] = handlers
      id
    }
  }

  fun presentation(id: Long): HostProfileHandlers? = synchronized(presentations) { presentations[id] }

  fun endPresentation(id: Long) {
    synchronized(presentations) { presentations.remove(id) }
  }

  fun post(action: () -> Unit) {
    main.post(action)
  }
}

/**
 * How the community UI on screen edits the profile.
 *
 * [handlers] is null when no view or presentation declared anything (a post
 * opened full screen from a notification): the sticky ownership then applies,
 * with nobody to hand the edit to.
 */
internal class ProfileEditing(
  private val handlers: HostProfileHandlers?,
  private val stepAside: (() -> Unit)? = null,
) {
  val ownsEditor: Boolean get() = handlers?.let { it.profile != null } ?: HostProfileEditing.ownsProfile
  val ownsPhoto: Boolean get() = handlers?.let { it.photo != null } ?: HostProfileEditing.ownsPhoto
  val ownsName: Boolean get() = handlers?.let { it.name != null } ?: HostProfileEditing.ownsName

  /** What opens the editor, or null when the host owns it and cannot be reached. */
  fun editorOpener(openSdkEditor: () -> Unit): (() -> Unit)? =
    if (!ownsEditor) openSdkEditor else hostOpener(handlers?.profile, closeSdkUi = {})

  /** Null when the photo is the SDK's, or the host's but unreachable: check [ownsPhoto]. */
  fun photoOpener(closeEditor: () -> Unit): (() -> Unit)? =
    if (ownsPhoto) hostOpener(handlers?.photo, closeEditor) else null

  /** Null when the name is the SDK's, or the host's but unreachable: check [ownsName]. */
  fun nameOpener(closeEditor: () -> Unit): (() -> Unit)? =
    if (ownsName) hostOpener(handlers?.name, closeEditor) else null

  // The SDK closes its own UI first: in Flutter or React Native, the host's
  // screen would otherwise open behind the community activity.
  private fun hostOpener(callback: (() -> Unit)?, closeSdkUi: () -> Unit): (() -> Unit)? =
    callback?.let { host ->
      {
        closeSdkUi()
        stepAside?.invoke()
        HostProfileEditing.post(host)
      }
    }
}

internal val LocalProfileEditing = staticCompositionLocalOf { ProfileEditing(handlers = null) }
