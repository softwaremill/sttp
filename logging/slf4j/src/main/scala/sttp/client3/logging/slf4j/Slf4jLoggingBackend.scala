package sttp.client3.logging.slf4j

import sttp.client3._
import sttp.client3.logging.LoggingBackend
import sttp.model.HeaderNames

object Slf4jLoggingBackend {
  def apply[F[_], S](
      delegate: SttpBackend[F, S],
      includeTiming: Boolean = true,
      beforeCurlInsteadOfShow: Boolean = false,
      logRequestBody: Boolean = false,
      logResponseBody: Boolean = false,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): SttpBackend[F, S] = {
    val logger = new Slf4jLogger("sttp.client3.logging.slf4j.Slf4jLoggingBackend", delegate.responseMonad)
    LoggingBackend(
      delegate,
      logger,
      includeTiming,
      beforeCurlInsteadOfShow,
      logRequestBody,
      logResponseBody,
      sensitiveHeaders
    )
  }
}
