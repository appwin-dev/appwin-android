package io.appwin.core

import kotlinx.serialization.Serializable

/**
 * What your app already knows about its user, shared by every Appwin product.
 *
 * Every field is optional, and an omitted (`null`) field is left untouched
 * server-side: an app that only knows the email does not erase a name set
 * earlier. Passed to [AppwinCore.identify] and [AppwinCore.updateUser].
 *
 * @property email contact email.
 * @property name display name, shown to your support team.
 * @property avatarUrl public URL of the user's picture.
 * @property language ISO 639-1 code, e.g. `fr`.
 * @property timezone IANA zone, e.g. `Europe/Paris`.
 * @property location free-form place, attached to this device.
 * @property plan your app's plan or tier, usable in segments.
 */
@Serializable
public data class AppwinUserAttributes(
  public val email: String? = null,
  public val name: String? = null,
  public val avatarUrl: String? = null,
  public val language: String? = null,
  public val timezone: String? = null,
  public val location: String? = null,
  public val plan: String? = null,
)
