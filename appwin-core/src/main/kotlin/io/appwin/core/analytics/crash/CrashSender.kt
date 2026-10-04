package io.appwin.core.analytics.crash

import io.appwin.core.analytics.IsoDate
import io.appwin.core.analytics.SendOutcome
import io.appwin.core.analytics.sendOutcome
import io.appwin.core.network.ApiClient
import io.appwin.core.network.HttpMethod
import kotlinx.serialization.Serializable

internal interface CrashSender {
  suspend fun send(reports: List<String>): SendOutcome
}

/** POSTs stored reports to `/api/sdk/v1/crashes` (ADR-0056), frozen URL. */
internal class ApiCrashSender(
  /** Resolved per send: the client does not exist before `configure()`. */
  private val client: () -> ApiClient?,
) : CrashSender {

  @Serializable
  private data class IngestResponse(val accepted: Int = 0, val rejected: Int = 0)

  override suspend fun send(reports: List<String>): SendOutcome {
    val client = client() ?: return SendOutcome.RETRYABLE
    // Stored reports are already wire JSON: joined, never re-parsed.
    val body =
      """{"crashes":[${reports.joinToString(",")}],"sentAt":"${IsoDate.format(System.currentTimeMillis())}"}"""
    return sendOutcome(label = "crash batch") {
      client.request("/api/sdk/v1/crashes", HttpMethod.POST, IngestResponse.serializer(), body)
      SendOutcome.OK
    }
  }
}
