package sttp.client3.logging

import java.util.concurrent.TimeUnit
import sttp.capabilities.Effect
import sttp.client3._
import sttp.client3.listener.{ListenerBackend, RequestListener}
import sttp.model.{HeaderNames, StatusCode}
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
      logRequestHeaders: Boolean = true,
      logResponseBody: Boolean = false,
      logResponseHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders,
      beforeRequestSendLogLevel: LogLevel = LogLevel.Debug,
      responseLogLevel: StatusCode => LogLevel = DefaultLog.defaultResponseLogLevel,
      responseExceptionLogLevel: LogLevel = LogLevel.Error
  ): SttpBackend[F, S] = {
    val log = new DefaultLog(
      logger,
      beforeCurlInsteadOfShow,
      logRequestBody,
      logRequestHeaders,
      logResponseHeaders,
      sensitiveHeaders,
      beforeRequestSendLogLevel,
      responseLogLevel,
      responseExceptionLogLevel
    )
    apply(delegate, log, includeTiming, logResponseBody)
  }

  def apply[F[_], S](delegate: SttpBackend[F, S], log: Log[F]): SttpBackend[F, S] =
    apply(delegate, log, includeTiming = true, logResponseBody = false)

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

class LoggingWithResponseBodyBackend[F[_], S](
    delegate: SttpBackend[F, S],
    log: Log[F],
    includeTiming: Boolean
) extends DelegateSttpBackend[F, S](delegate) {
  private def now(): Long = System.currentTimeMillis()
  private def elapsed(from: Option[Long]): Option[Duration] = from.map(f => Duration(now() - f, TimeUnit.MILLISECONDS))

  override def send[T, R >: S with Effect[F]](request: AbstractRequest[T, R]): F[Response[T]] = {
    log.beforeRequestSend(request).flatMap { _ =>
      val start = if (includeTiming) Some(now()) else None
      def sendAndLog(request: AbstractRequest[(T, Option[String]), S]): F[Response[T]] = {
        for {
          r <- delegate.send(request)
          _ <- log.response(request, r, r.body._2, elapsed(start))
        } yield r.copy(body = r.body._1)
      }
      val response = request match {
        case request: Request[T] =>
          sendAndLog(request.response(asBothOption(request.response, asStringAlways)))
        case request: StreamRequest[T, S] =>
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
          .flatMap(_ => responseMonad.error(e))
      }
    }
  }
}
