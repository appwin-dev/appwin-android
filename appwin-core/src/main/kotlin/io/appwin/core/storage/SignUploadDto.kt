package io.appwin.core.storage

import kotlinx.serialization.Serializable

@Serializable
internal data class SignUploadBody(
  val mimeType: String,
  val sizeBytes: Int,
)

@Serializable
internal data class SignUploadResponse(
  val storageKey: String,
  val postUrl: String,
  val fields: Map<String, String>,
  val expiresInSec: Int = 300,
)
