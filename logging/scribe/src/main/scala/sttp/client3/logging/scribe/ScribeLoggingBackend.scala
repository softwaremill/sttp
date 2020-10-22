package sttp.client3.logging.scribe

import sttp.client3._
import sttp.client3.logging.LoggingBackend
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
