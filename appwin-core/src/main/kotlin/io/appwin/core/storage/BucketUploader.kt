package io.appwin.core.storage

import io.appwin.core.network.AppwinApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Multipart POST to S3 from a presigned policy (ADR-0022).
 *
 * Uses a bare [OkHttpClient]: the bucket must not receive Appwin auth headers.
 */
internal object BucketUploader {
  private val http = OkHttpClient()

  suspend fun upload(
    data: ByteArray,
    mimeType: String,
    filename: String,
    postUrl: String,
    fields: Map<String, String>,
    onProgress: (Double) -> Unit = {},
  ) = withContext(Dispatchers.IO) {
    val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
    // Policy fields first, then the file - S3 requires that order.
    for ((key, value) in fields) {
      bodyBuilder.addFormDataPart(key, value)
    }
    bodyBuilder.addFormDataPart(
      "file",
      filename,
      data.toRequestBody(mimeType.toMediaType()),
    )

    val request = Request.Builder().url(postUrl).post(bodyBuilder.build()).build()
    onProgress(0.0)

    val response = http.newCall(request).await()
    response.use {
      onProgress(1.0)
      if (!it.isSuccessful) {
        throw AppwinApiException.Http(it.code, it.body?.string()?.ifEmpty { null })
      }
    }
  }

  private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
      enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(AppwinApiException.Network(e))
          }

          override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response)
          }
        },
      )
      cont.invokeOnCancellation { cancel() }
    }
}
