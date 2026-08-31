package io.appwin.core.network

import java.io.IOException

/**
 * Errors surfaced by the SDK's HTTP client.
 *
 * A closed hierarchy rather than raw `IOException`s: the host app must be able
 * to tell "no network" (its problem, retryable) from "the server refused" (a
 * 4xx, often a configuration error) without
 * inspecter des messages.
 */
public sealed class AppwinApiException(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause) {

  /** The request never left, or the response never arrived. */
  public class Network(cause: Throwable) : AppwinApiException("Network failure", cause)

  /** The server answered outside the 2xx range. */
  public class Http(
    public val status: Int,
    public val body: String?,
  ) : AppwinApiException("HTTP $status")

  /** The response is not the expected shape. */
  public class Decoding(cause: Throwable) : AppwinApiException("Decoding failure", cause)

  /** `configure()` was not called, or was called with a blank app id. */
  public class NotConfigured : AppwinApiException(
    "AppwinCore.configure(context, projectAppId) must be called before using the SDK",
  )

  /**
   * End-user message, separating a client-side problem from a server-side one.
   * The technical detail stays on the exception, for the logs.
   */
  public val userMessage: String
    get() = when (this) {
      is Network -> "Problème de connexion. Vérifiez votre réseau et réessayez."
      is Http -> if (status in 500..599) {
        "Le serveur a rencontré un problème. Réessayez dans un instant."
      } else {
        "L'envoi a été refusé par le serveur."
      }
      is Decoding, is NotConfigured -> "Une erreur inattendue est survenue."
    }
}

/** True when the failure is a network one and deserves a retry. */
public fun Throwable.isAppwinRetryable(): Boolean = when (this) {
  is AppwinApiException.Network -> true
  is AppwinApiException.Http -> status in 500..599 || status == 429
  is IOException -> true
  else -> false
}
