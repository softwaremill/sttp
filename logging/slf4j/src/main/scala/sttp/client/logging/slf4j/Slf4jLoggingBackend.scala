package sttp.client.logging.slf4j

import sttp.client._
import sttp.client.logging.LoggingBackend

object Slf4jLoggingBackend {
  def apply[F[_], S](
      delegate: SttpBackend[F, S],
      includeTiming: Boolean = true,
      beforeCurlInsteadOfShow: Boolean = false,
      logRequestBody: Boolean = false,
      logResponseBody: Boolean = false
  ): SttpBackend[F, S] = {
    val logger = new Slf4jLogger("sttp.client.logging.slf4j.Slf4jLoggingBackend", delegate.responseMonad)
    LoggingBackend(delegate, logger, includeTiming, beforeCurlInsteadOfShow, logRequestBody, logResponseBody)
  }
}
