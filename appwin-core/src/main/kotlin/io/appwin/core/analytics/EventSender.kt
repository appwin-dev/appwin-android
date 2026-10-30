@file:OptIn(AppwinInternalApi::class)

package io.appwin.core.analytics

import android.util.Log
import io.appwin.core.AppwinInternalApi
import io.appwin.core.network.ApiClient
import io.appwin.core.network.AppwinApiException
import io.appwin.core.network.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * What an uploader does with a batch after one send attempt. Each case maps
 * to one branch of the retry policy - the uploaders never see HTTP. Shared by
 * every SDK upload (events, crash reports, replay segments).
 */
@AppwinInternalApi
public enum class SendOutcome {
  /** Server ingested the batch: delete the file. */
  OK,

  /**
   * Server dropped the batch by design (hard-capped plan): delete the file,
   * do NOT retry, and cool down before the next attempt.
   */
  QUOTA_EXCEEDED,

  /** Bearer refused: re-bootstrap the session once, then retry. */
  UNAUTHORIZED,

  /**
   * 403: the product is switched off for this project. Events and crash
   * reports treat it as [FATAL]; replay stops and purges.
   */
  FORBIDDEN,

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
    return sendOutcome(label = "analytics batch") {
      val response =
        client.request("/api/sdk/v1/events", HttpMethod.POST, IngestResponse.serializer(), body)
      if (response.quotaExceeded == true) SendOutcome.QUOTA_EXCEEDED else SendOutcome.OK
    }
  }
}

/**
 * The retry policy's view of one POST, shared by every SDK upload (events,
 * crash reports, replay segments) so they back off and give up on exactly the
 * same answers.
 */
internal suspend fun sendOutcome(label: String, send: suspend () -> SendOutcome): SendOutcome =
  try {
    send()
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (error: AppwinApiException.Http) {
    when {
      error.status == 401 -> SendOutcome.UNAUTHORIZED
      error.status == 403 -> {
        Log.w("Appwin", "$label refused (403): ${error.body}")
        SendOutcome.FORBIDDEN
      }
      error.status == 408 || error.status == 429 || error.status in 500..599 ->
        SendOutcome.RETRYABLE
      else -> {
        Log.w("Appwin", "$label refused (${error.status}): ${error.body}")
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
