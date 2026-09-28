package io.appwin.core.storage

import io.appwin.core.network.ApiClient
import io.appwin.core.network.HttpMethod

/** Core sign-upload flow (ADR-0022), behind `AppwinCore.uploadMedia`. */
internal object MediaUploads {
  suspend fun upload(
    api: ApiClient,
    data: ByteArray,
    mimeType: String,
    filename: String,
    onProgress: (Double) -> Unit,
  ): UploadedMediaRef {
    val signed = api.request(
      path = "/api/sdk/v1/storage/sign-upload",
      method = HttpMethod.POST,
      deserializer = SignUploadResponse.serializer(),
      body = ApiClient.json.encodeToString(
        SignUploadBody.serializer(),
        SignUploadBody(mimeType = mimeType, sizeBytes = data.size),
      ),
    )

    BucketUploader.upload(
      data = data,
      mimeType = mimeType,
      filename = filename,
      postUrl = signed.postUrl,
      fields = signed.fields,
      onProgress = onProgress,
    )

    return UploadedMediaRef(
      storageKey = signed.storageKey,
      mimeType = mimeType,
      sizeBytes = data.size,
      filename = filename,
    )
  }
}
