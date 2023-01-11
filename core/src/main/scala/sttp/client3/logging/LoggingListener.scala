package sttp.client3.logging

import java.util.concurrent.TimeUnit
import sttp.client3.{AbstractRequest, Response}
import sttp.client3.listener.RequestListener
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.concurrent.duration.Duration

class LoggingListener[F[_]](log: Log[F], includeTiming: Boolean)(implicit m: MonadError[F])
    extends RequestListener[F, Option[Long]] {
  private def now(): Long = System.currentTimeMillis()
  private def elapsed(from: Option[Long]): Option[Duration] = from.map(f => Duration(now() - f, TimeUnit.MILLISECONDS))

  override def beforeRequest(request: AbstractRequest[_, _]): F[Option[Long]] = {
    log.beforeRequestSend(request).map(_ => if (includeTiming) Some(now()) else None)
  }

  override def requestException(request: AbstractRequest[_, _], tag: Option[Long], e: Exception): F[Unit] = {
    log.requestException(request, elapsed(tag), e)
  }

  override def requestSuccessful(request: AbstractRequest[_, _], response: Response[_], tag: Option[Long]): F[Unit] = {
    log.response(request, response, None, elapsed(tag))
  }
}
