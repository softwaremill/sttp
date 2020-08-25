package sttp.client.logging.slf4j

import org.slf4j.{Logger, LoggerFactory}
import sttp.capabilities.Effect
import sttp.client._
import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.logging.LogMessages
import sttp.monad.MonadError
import sttp.monad.syntax._

object Slf4jLoggingBackend {
  private val logger = LoggerFactory.getLogger("sttp.client.logging.slf4j.Slf4jLoggingBackend")

  def apply[F[_], S](
      delegate: SttpBackend[F, S],
      logRequestBody: Boolean = false,
      logResponseBody: Boolean = false
  ): SttpBackend[F, S] =
    if (logResponseBody) new Slf4jLoggingWithResponseBodyBackend(delegate, logger, logRequestBody)
    else
      ListenerBackend.lift(delegate, new Slf4jLoggingListener(logger, logRequestBody))
}

class Slf4jLoggingListener(logger: Logger, logRequestBody: Boolean) extends RequestListener[Identity, Unit] {
  override def beforeRequest(request: Request[_, _]): Identity[Unit] = {
    logger.debug(LogMessages.beforeRequestSend(request))
  }

  override def requestException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    logger.error(LogMessages.requestException(request, logRequestBody), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Unit): Identity[Unit] = {
    logger.debug(LogMessages.response(request, response, logRequestBody, None))
  }
}

class Slf4jLoggingWithResponseBodyBackend[F[_], S](delegate: SttpBackend[F, S], logger: Logger, logRequestBody: Boolean)
    extends SttpBackend[F, S] {
  override def send[T, R >: S with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    (for {
      _ <- responseMonad.eval(logger.debug(LogMessages.beforeRequestSend(request)))
      response <- request.response(asBothOption(request.response, asStringAlways)).send(delegate)
      _ <- responseMonad.eval(logger.debug(LogMessages.response(request, response, logRequestBody, response.body._2)))
    } yield response.copy(body = response.body._1))
      .handleError {
        case e: Exception =>
          responseMonad
            .eval(logger.error(LogMessages.requestException(request, logRequestBody), e))
            .flatMap(_ => responseMonad.error(e))
      }
  }

  override def close(): F[Unit] = delegate.close()
  override implicit def responseMonad: MonadError[F] = delegate.responseMonad
}
