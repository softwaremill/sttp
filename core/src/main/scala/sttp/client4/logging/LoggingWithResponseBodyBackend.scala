package sttp.client4.logging

import java.util.concurrent.TimeUnit
import sttp.client4._
import sttp.monad.syntax._

import scala.concurrent.duration.Duration
import sttp.capabilities.Effect
import sttp.client4.wrappers.DelegateBackend
import sttp.shared.Identity

abstract class LoggingWithResponseBodyBackend[F[_], P](
    delegate: GenericBackend[F, P],
    log: Log[F],
    includeTiming: Boolean
) extends DelegateBackend[F, P](delegate) {

  private def now(): Long = System.currentTimeMillis()
  private def elapsed(from: Option[Long]): Option[Duration] = from.map(f => Duration(now() - f, TimeUnit.MILLISECONDS))

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] =
    log.beforeRequestSend(request).flatMap { _ =>
      val start = if (includeTiming) Some(now()) else None
      def sendAndLog(request: GenericRequest[(T, Option[String]), P with Effect[F]]): F[Response[T]] =
        for {
          r <- delegate.send(request)
          _ <- log.response(request, r, r.body._2, elapsed(start))
        } yield r.copy(body = r.body._1)
      val response = request match {
        case request: Request[T] =>
          sendAndLog(request.response(asBothOption(request.response, asStringAlways)))
        case request: StreamRequest[T, P with Effect[F]] =>
          sendAndLog(request.response(asBothOption(request.response, asStringAlways)))
        case request =>
          for {
            r <- delegate.send(request)
            _ <- log.response(request, r, None, elapsed(start))
          } yield r
      }
      response.handleError { case e: Exception =>
        log
          .requestException(request, elapsed(start), e)
          .flatMap(_ => monad.error(e))
      }
    }
}

object LoggingWithResponseBodyBackend {
  def apply(backend: SyncBackend, log: Log[Identity], includeTiming: Boolean): SyncBackend =
    new LoggingWithResponseBodyBackend(backend, log, includeTiming) with SyncBackend {}

  def apply[F[_]](backend: Backend[F], log: Log[F], includeTiming: Boolean): Backend[F] =
    new LoggingWithResponseBodyBackend(backend, log, includeTiming) with Backend[F] {}

  def apply[F[_]](backend: WebSocketBackend[F], log: Log[F], includeTiming: Boolean): WebSocketBackend[F] =
    new LoggingWithResponseBodyBackend(backend, log, includeTiming) with WebSocketBackend[F] {}

  def apply(backend: WebSocketSyncBackend, log: Log[Identity], includeTiming: Boolean): WebSocketSyncBackend =
    new LoggingWithResponseBodyBackend(backend, log, includeTiming) with WebSocketSyncBackend {}

  def apply[F[_], S](backend: StreamBackend[F, S], log: Log[F], includeTiming: Boolean): StreamBackend[F, S] =
    new LoggingWithResponseBodyBackend(backend, log, includeTiming) with StreamBackend[F, S] {}

  def apply[F[_], S](
      backend: WebSocketStreamBackend[F, S],
      log: Log[F],
      includeTiming: Boolean
  ): WebSocketStreamBackend[F, S] =
    new LoggingWithResponseBodyBackend(backend, log, includeTiming) with WebSocketStreamBackend[F, S] {}
}
