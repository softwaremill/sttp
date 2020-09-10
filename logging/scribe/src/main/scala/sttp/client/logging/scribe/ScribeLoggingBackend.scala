package sttp.client.logging.scribe

import sttp.client._
import sttp.client.logging.LoggingBackend
import sttp.model.HeaderNames

object ScribeLoggingBackend {
  def apply[F[_], S](
      delegate: SttpBackend[F, S],
      includeTiming: Boolean = true,
      beforeCurlInsteadOfShow: Boolean = false,
      logRequestBody: Boolean = false,
      logResponseBody: Boolean = false,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): SttpBackend[F, S] =
    LoggingBackend(
      delegate,
      logger = ScribeLogger(delegate.responseMonad),
      includeTiming,
      beforeCurlInsteadOfShow,
      logRequestBody,
      logResponseBody,
      sensitiveHeaders
    )
}
