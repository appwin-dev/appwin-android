package io.appwin.core.analytics

import android.util.Log
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * What the pipeline does with a batch after one send attempt. Each case maps
 * to one branch of the retry policy - the pipeline never sees HTTP.
 */
internal enum class SendOutcome {
  /** Server ingested the batch: delete the file. */
  OK,

  /**
   * Server dropped the batch by design (hard-capped plan): delete the file,
   * do NOT retry, and cool down before the next attempt.
   */
  QUOTA_EXCEEDED,

  /** Bearer refused: re-bootstrap the session once, then retry. */
  UNAUTHORIZED,

  /** Transient (network, 408, 429, 5xx): keep the file, back off. */
  RETRYABLE,

  /**
   * Deterministic refusal (400 and other 4xx): a bug, retrying would loop
   * forever and block the queue behind it. Drop the file, log loudly.
   */
  FATAL,
}

internal interface EventSender {
  suspend fun send(lines: List<String>): SendOutcome
}

/** Real sender: wraps the wire lines in the ingest envelope and POSTs them. */
internal class ApiEventSender(
  /** Resolved per send: the client does not exist before `configure()`. */
  private val client: () -> ApiClient?,
) : EventSender {

  @Serializable
  private data class IngestResponse(
    val accepted: Int = 0,
    val rejected: Int = 0,
    val quotaExceeded: Boolean? = null,
  )

  override suspend fun send(lines: List<String>): SendOutcome {
    val client = client() ?: return SendOutcome.RETRYABLE
    // Lines are already wire-format JSON objects: the envelope is assembled
    // by joining them, never by re-parsing.
    val body =
      """{"events":[${lines.joinToString(",")}],"sentAt":"${IsoDate.format(System.currentTimeMillis())}"}"""
    return try {
      val response =
        client.request("/api/sdk/v1/events", HttpMethod.POST, IngestResponse.serializer(), body)
      if (response.quotaExceeded == true) SendOutcome.QUOTA_EXCEEDED else SendOutcome.OK
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: AppwinApiException.Http) {
      when {
        error.status == 401 -> SendOutcome.UNAUTHORIZED
        error.status == 408 || error.status == 429 || error.status in 500..599 ->
          SendOutcome.RETRYABLE
        else -> {
          Log.w(TAG, "analytics batch refused (${error.status}): ${error.body}")
          SendOutcome.FATAL
        }
      }
    } catch (error: AppwinApiException.Decoding) {
      // 2xx reached: the server ingested the batch, only our parse of the
      // response failed. Deleting is right; resending would duplicate.
      SendOutcome.OK
    } catch (error: Exception) {
      SendOutcome.RETRYABLE
    }
  }

  private companion object {
    const val TAG = "Appwin"
  }
}
