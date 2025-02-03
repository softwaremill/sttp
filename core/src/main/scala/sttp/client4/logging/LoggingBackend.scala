package sttp.client4.logging

import sttp.capabilities.Effect
import sttp.client4._
import sttp.client4.logging.LoggingBackend.LoggingTag
import sttp.client4.wrappers.DelegateBackend
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.monad.syntax._
import sttp.shared.Identity

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.Duration

class LoggingBackend[F[_], P](
    delegate: GenericBackend[F, P],
    log: Log[F],
    logResponseBody: Boolean,
    includeTimings: Boolean
) extends DelegateBackend[F, P](delegate) {

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
    if (request.loggingOptions.log) {
      log.beforeRequestSend(request).flatMap { _ =>
        val _includeTimings = request.loggingOptions.includeTimings.getOrElse(includeTimings)
        val (requestWithTimings, tag) = if (_includeTimings) {
          val t = LoggingTag(now(), new AtomicLong(0L))
          (request.onBodyReceived(() => t.bodyReceived.set(now())), Some(t))
        } else (request, None)

        monad.handleError {
          val _logResponseBody = request.loggingOptions.logResponseBody.getOrElse(logResponseBody)
          if (_logResponseBody) sendLogResponseBody(requestWithTimings, tag)
          else sendDoNotLogResponseBody(requestWithTimings, tag)
        } { case e: Exception =>
          monad.flatMap {
            ResponseException.find(e) match {
              case Some(re) => log.response(request, re.response, None, tag.map(toResponseTimings), Some(e))
              case None     => log.requestException(request, tag.map(toResponseTimings).map(_.bodyProcessed), e)
            }
          } { _ => monad.error(e) }
        }
      }
    } else {
      delegate.send(request)
    }
  }

  private def sendDoNotLogResponseBody[T](
      request: GenericRequest[T, P with Effect[F]],
      tag: Option[LoggingTag]
  ): F[Response[T]] = {
    for {
      r <- delegate.send(request)
      _ <- log.response(request, r, None, tag.map(toResponseTimings), None)
    } yield r
  }

  private def sendLogResponseBody[T](
      request: GenericRequest[T, P with Effect[F]],
      tag: Option[LoggingTag]
  ): F[Response[T]] = {
    def sendAndLog(request: GenericRequest[(T, Option[String]), P with Effect[F]]): F[Response[T]] =
      for {
        r <- delegate.send(request)
        _ <- log.response(request, r, r.body._2, tag.map(toResponseTimings), None)
      } yield r.copy(body = r.body._1)

    request match {
      case request: Request[T] @unchecked =>
        sendAndLog(request.response(asBothOption(request.response, asStringAlways)))
      case request: StreamRequest[T, P with Effect[F]] @unchecked =>
        sendAndLog(request.response(asBothOption(request.response, asStringAlways)))
      case request =>
        for {
          r <- delegate.send(request)
          _ <- log.response(request, r, None, tag.map(toResponseTimings), None)
        } yield r
    }
  }

  private def now(): Long = System.currentTimeMillis()
  private def elapsed(from: Long): Duration = Duration(now() - from, TimeUnit.MILLISECONDS)
  private def toResponseTimings(tag: LoggingTag): ResponseTimings = {
    val br = tag.bodyReceived.get()
    ResponseTimings(if (br == 0) None else Some(elapsed(br)), elapsed(tag.start))
  }
}

/** The logging backend uses the given [[Logger]] instance (which provides integration with an underlying logging
  * library), to log information before sending a request, and after a response is received.
  *
  * The information included in the log messages is configurable via [[LogConfig]]. Additionally, the formatting of the
  * messages can be changed by providing a custom [[Log]] implementation.
  *
  * Configuration for individual requests can be partially altered by using [[Request.loggingOptions]].
  */
object LoggingBackend {
  def apply(delegate: SyncBackend, logger: Logger[Identity]): SyncBackend =
    apply(delegate, logger, LogConfig.Default)

  def apply[F[_]](delegate: Backend[F], logger: Logger[F]): Backend[F] =
    apply(delegate, logger, LogConfig.Default)

  def apply[F[_]](delegate: WebSocketBackend[F], logger: Logger[F]): WebSocketBackend[F] =
    apply(delegate, logger, LogConfig.Default)

  def apply(delegate: WebSocketSyncBackend, logger: Logger[Identity]): WebSocketSyncBackend =
    apply(delegate, logger, LogConfig.Default)

  def apply[F[_], S](delegate: StreamBackend[F, S], logger: Logger[F]): StreamBackend[F, S] =
    apply(delegate, logger, LogConfig.Default)

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S], logger: Logger[F]): WebSocketStreamBackend[F, S] =
    apply(delegate, logger, LogConfig.Default)

  def apply(delegate: SyncBackend, logger: Logger[Identity], config: LogConfig): SyncBackend =
    apply(delegate, Log.default(logger, config), config.includeTimings, config.logResponseBody)

  def apply[F[_]](delegate: Backend[F], logger: Logger[F], config: LogConfig): Backend[F] =
    apply(delegate, Log.default(logger, config), config.includeTimings, config.logResponseBody)

  def apply[F[_]](delegate: WebSocketBackend[F], logger: Logger[F], config: LogConfig): WebSocketBackend[F] =
    apply(delegate, Log.default(logger, config), config.includeTimings, config.logResponseBody)

  def apply(delegate: WebSocketSyncBackend, logger: Logger[Identity], config: LogConfig): WebSocketSyncBackend =
    apply(delegate, Log.default(logger, config), config.includeTimings, config.logResponseBody)

  def apply[F[_], S](delegate: StreamBackend[F, S], logger: Logger[F], config: LogConfig): StreamBackend[F, S] =
    apply(delegate, Log.default(logger, config), config.includeTimings, config.logResponseBody)

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      logger: Logger[F],
      config: LogConfig
  ): WebSocketStreamBackend[F, S] =
    apply(delegate, Log.default(logger, config), config.includeTimings, config.logResponseBody)

  def apply(delegate: SyncBackend, log: Log[Identity], includeTimings: Boolean, logResponseBody: Boolean): SyncBackend =
    // redirects should be handled before logging
    FollowRedirectsBackend(new LoggingBackend(delegate, log, includeTimings, logResponseBody) with SyncBackend {})

  def apply[F[_]](delegate: Backend[F], log: Log[F], includeTimings: Boolean, logResponseBody: Boolean): Backend[F] =
    FollowRedirectsBackend(new LoggingBackend(delegate, log, includeTimings, logResponseBody) with Backend[F] {})

  def apply[F[_]](
      delegate: WebSocketBackend[F],
      log: Log[F],
      includeTimings: Boolean,
      logResponseBody: Boolean
  ): WebSocketBackend[F] =
    FollowRedirectsBackend(
      new LoggingBackend(delegate, log, includeTimings, logResponseBody) with WebSocketBackend[F] {}
    )

  def apply(
      delegate: WebSocketSyncBackend,
      log: Log[Identity],
      includeTimings: Boolean,
      logResponseBody: Boolean
  ): WebSocketSyncBackend =
    FollowRedirectsBackend(
      new LoggingBackend(delegate, log, includeTimings, logResponseBody) with WebSocketSyncBackend {}
    )

  def apply[F[_], S](
      delegate: StreamBackend[F, S],
      log: Log[F],
      includeTimings: Boolean,
      logResponseBody: Boolean
  ): StreamBackend[F, S] =
    FollowRedirectsBackend(
      new LoggingBackend(delegate, log, includeTimings, logResponseBody) with StreamBackend[F, S] {}
    )

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      log: Log[F],
      includeTimings: Boolean,
      logResponseBody: Boolean
  ): WebSocketStreamBackend[F, S] =
    FollowRedirectsBackend(
      new LoggingBackend(delegate, log, includeTimings, logResponseBody) with WebSocketStreamBackend[F, S] {}
    )

  private case class LoggingTag(start: Long, bodyReceived: AtomicLong)
}
