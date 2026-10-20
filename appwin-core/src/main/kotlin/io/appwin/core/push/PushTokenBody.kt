package io.appwin.core.push

import kotlinx.serialization.Serializable

@Serializable
internal data class PushTokenBody(
  val token: String,
  val platform: String,
  val pushOptIn: Boolean,
)
