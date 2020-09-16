package sttp.client3.logging

import java.util.concurrent.TimeUnit

import sttp.capabilities.Effect
import sttp.client3._
import sttp.client3.listener.{ListenerBackend, RequestListener}
import sttp.model.HeaderNames
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.concurrent.duration.Duration

object LoggingBackend {
  def apply[F[_], S](
      delegate: SttpBackend[F, S],
      logger: Logger[F],
      includeTiming: Boolean = true,
      beforeCurlInsteadOfShow: Boolean = false,
      logRequestBody: Boolean = false,
      logResponseBody: Boolean = false,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): SttpBackend[F, S] = {
    val log = new DefaultLog(logger, beforeCurlInsteadOfShow, logRequestBody, sensitiveHeaders)
    apply(delegate, log, includeTiming, logResponseBody)
  }

  def apply[F[_], S](
      delegate: SttpBackend[F, S],
      log: Log[F],
      includeTiming: Boolean,
      logResponseBody: Boolean
  ): SttpBackend[F, S] = {
    implicit val m: MonadError[F] = delegate.responseMonad
    if (logResponseBody) new LoggingWithResponseBodyBackend(delegate, log, includeTiming)
    else
      new ListenerBackend(delegate, new LoggingListener(log, includeTiming))
  }
}

class LoggingListener[F[_]](log: Log[F], includeTiming: Boolean)(implicit m: MonadError[F])
    extends RequestListener[F, Option[Long]] {
  private def now(): Long = System.currentTimeMillis()
  private def elapsed(from: Option[Long]): Option[Duration] = from.map(f => Duration(now() - f, TimeUnit.MILLISECONDS))

  override def beforeRequest(request: Request[_, _]): F[Option[Long]] = {
    log.beforeRequestSend(request).map(_ => if (includeTiming) Some(now()) else None)
  }

  override def requestException(request: Request[_, _], tag: Option[Long], e: Exception): F[Unit] = {
    log.requestException(request, elapsed(tag), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Option[Long]): F[Unit] = {
    log.response(request, response, None, elapsed(tag))
  }
}

class LoggingWithResponseBodyBackend[F[_], S](
    delegate: SttpBackend[F, S],
    log: Log[F],
    includeTiming: Boolean
) extends SttpBackend[F, S] {
  private def now(): Long = System.currentTimeMillis()
  private def elapsed(from: Option[Long]): Option[Duration] = from.map(f => Duration(now() - f, TimeUnit.MILLISECONDS))

  override def send[T, R >: S with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    log.beforeRequestSend(request).flatMap { _ =>
      val start = if (includeTiming) Some(now()) else None
      (for {
        response <- request.response(asBothOption(request.response, asStringAlways)).send(delegate)
        _ <- log.response(request, response, response.body._2, elapsed(start))
      } yield response.copy(body = response.body._1))
        .handleError { case e: Exception =>
          log
            .requestException(request, elapsed(start), e)
            .flatMap(_ => responseMonad.error(e))
        }
    }
  }

  override def close(): F[Unit] = delegate.close()
  override implicit def responseMonad: MonadError[F] = delegate.responseMonad
}
