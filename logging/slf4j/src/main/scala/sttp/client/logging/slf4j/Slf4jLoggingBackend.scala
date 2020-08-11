package sttp.client.logging.slf4j

import org.slf4j.{Logger, LoggerFactory}
import sttp.client._
import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.logging.LogMessages

object Slf4jLoggingBackend {
  private val logger = LoggerFactory.getLogger("sttp.client.logging.slf4j.Slf4jLoggingBackend")

  def apply[F[_], S](delegate: SttpBackend[F, S]): SttpBackend[F, S] =
    ListenerBackend.lift(delegate, new Slf4jLoggingListener(logger))
}

class Slf4jLoggingListener(logger: Logger) extends RequestListener[Identity, Unit] {
  override def beforeRequest(request: Request[_, _]): Identity[Unit] = {
    logger.debug(LogMessages.beforeRequestSend(request))
  }

  override def requestException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    logger.error(LogMessages.requestException(request), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Unit): Identity[Unit] = {
    if (response.isSuccess) {
      logger.debug(LogMessages.response(request, response))
    } else {
      logger.debug(LogMessages.response(request, response))
    }
  }
}
