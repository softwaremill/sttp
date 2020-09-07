package sttp.client.logging.slf4j

import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.logging.LogMessages
import sttp.client.{Identity, Request, Response, SttpBackend}

object Slf4jTimingBackend {
  private val logger = new Logger("sttp.client.logging.slf4j.Slf4jTimingBackend")

  def apply[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S]): SttpBackend[F, S] =
    ListenerBackend.lift(delegate, new Slf4jTimingListener(logger))
}

class Slf4jTimingListener(logger: Logger) extends RequestListener[Identity, Long] {
  private def now(): Long = System.currentTimeMillis()
  private def elapsed(from: Long): Long = now() - from

  override def beforeRequest(request: Request[_, _]): Identity[Long] = now()

  override def requestException(request: Request[_, _], tag: Long, e: Exception): Identity[Unit] = {
    logger.info(LogMessages.requestTiming(request, "exception", elapsed(tag)), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Long): Identity[Unit] = {
    logger.info(LogMessages.requestTiming(request, response.code.toString(), elapsed(tag)))
  }
}
