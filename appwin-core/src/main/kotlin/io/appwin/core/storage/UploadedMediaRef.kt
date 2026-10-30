package io.appwin.core.storage

/**
 * Result of [io.appwin.core.AppwinCore.uploadMedia].
 *
 * Hand `storageKey` to the product attach endpoint (Support message, Community
 * post, …). The object itself is not a downloadable URL.
 */
public data class UploadedMediaRef(
  public val storageKey: String,
  public val mimeType: String,
  public val sizeBytes: Int,
  public val filename: String,
)
