package io.appwin.support

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import io.appwin.core.AppwinCore
import io.appwin.support.data.ApiSupportRepository
import io.appwin.support.data.SupportRepository
import io.appwin.support.domain.Customer
import io.appwin.support.domain.SupportUserAttributes
import io.appwin.support.ui.MessengerRoot

/**
 * Appwin Support SDK for Android.
 *
 * The whole interface is native and comes from the SDK: your app provides an
 * entry point, the SDK draws the help centre, the FAQ and the conversations.
 * Customisation - colours, agent name, welcome message, FAQ - goes through the
 * dashboard and is re-read on every open.
 *
 * The contract follows the iOS SDK, platform idioms aside.
 *
 * [AppwinCore.configure] must have been called first.
 */
public object AppwinSupport {
  public const val VERSION: String = "0.1.0-dev"

  private val repository: SupportRepository by lazy { ApiSupportRepository() }

  /** The messenger, to embed in your own navigation. */
  @Composable
  public fun MessengerView() {
    MessengerRoot(onClose = null)
  }

  /** The messenger full screen, with its close button. */
  @JvmStatic
  public fun presentMessenger(context: Context) {
    context.startActivity(Intent(context, MessengerActivity::class.java))
  }

  /**
   * Attaches the device to your app's user.
   *
   * The identity is owned by Core and shared by every product: after this call,
   * the same person is recognised by Community.
   */
  @JvmStatic
  public fun loginIdentifiedUser(externalId: String) {
    AppwinCore.identify(externalId)
  }

  /**
   * Enriches the current customer with what your app already knows.
   *
   * Does **not** change identity; that is [loginIdentifiedUser]. Every field is
   * optional and an omitted one is not overwritten.
   */
  @JvmStatic
  public suspend fun updateUser(attributes: SupportUserAttributes): Customer =
    repository.identify(attributes)

  /** Revokes the session and goes back to an anonymous customer. */
  @JvmStatic
  public suspend fun logout() {
    AppwinCore.signOut()
  }

  /**
   * Registers this device's push token. Call again on every token rotation.
   *
   * Set `pushOptIn` to `false` rather than stopping registration: that
   * distinguishes "declined" from "never asked".
   */
  @JvmStatic
  @JvmOverloads
  public suspend fun registerPushToken(
    token: String,
    platform: String = "android",
    pushOptIn: Boolean = true,
  ) {
    require(token.isNotBlank()) { "token must not be blank" }
    repository.registerPushToken(token, platform, pushOptIn)
  }
}
