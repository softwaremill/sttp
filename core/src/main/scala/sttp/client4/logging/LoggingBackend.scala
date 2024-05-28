package sttp.client4.logging

import sttp.client4._
import sttp.client4.listener.ListenerBackend
import sttp.shared.Identity

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
    apply(delegate, Log.default(logger, config), config.includeTiming, config.logResponseBody)

  def apply[F[_]](delegate: Backend[F], logger: Logger[F], config: LogConfig): Backend[F] =
    apply(delegate, Log.default(logger, config), config.includeTiming, config.logResponseBody)

  def apply[F[_]](delegate: WebSocketBackend[F], logger: Logger[F], config: LogConfig): WebSocketBackend[F] =
    apply(delegate, Log.default(logger, config), config.includeTiming, config.logResponseBody)

  def apply(delegate: WebSocketSyncBackend, logger: Logger[Identity], config: LogConfig): WebSocketSyncBackend =
    apply(delegate, Log.default(logger, config), config.includeTiming, config.logResponseBody)

  def apply[F[_], S](delegate: StreamBackend[F, S], logger: Logger[F], config: LogConfig): StreamBackend[F, S] =
    apply(delegate, Log.default(logger, config), config.includeTiming, config.logResponseBody)

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      logger: Logger[F],
      config: LogConfig
  ): WebSocketStreamBackend[F, S] =
    apply(delegate, Log.default(logger, config), config.includeTiming, config.logResponseBody)

  def apply(delegate: SyncBackend, log: Log[Identity], includeTiming: Boolean, logResponseBody: Boolean): SyncBackend =
    if (logResponseBody) LoggingWithResponseBodyBackend(delegate, log, includeTiming)
    else ListenerBackend(delegate, new LoggingListener(log, includeTiming)(delegate.monad))

  def apply[F[_]](delegate: Backend[F], log: Log[F], includeTiming: Boolean, logResponseBody: Boolean): Backend[F] =
    if (logResponseBody) LoggingWithResponseBodyBackend[F](delegate, log, includeTiming)
    else ListenerBackend[F, Option[Long]](delegate, new LoggingListener(log, includeTiming)(delegate.monad))

  def apply[F[_]](
      delegate: WebSocketBackend[F],
      log: Log[F],
      includeTiming: Boolean,
      logResponseBody: Boolean
  ): WebSocketBackend[F] =
    if (logResponseBody) LoggingWithResponseBodyBackend(delegate, log, includeTiming)
    else ListenerBackend(delegate, new LoggingListener(log, includeTiming)(delegate.monad))

  def apply(
      delegate: WebSocketSyncBackend,
      log: Log[Identity],
      includeTiming: Boolean,
      logResponseBody: Boolean
  ): WebSocketSyncBackend =
    if (logResponseBody) LoggingWithResponseBodyBackend(delegate, log, includeTiming)
    else ListenerBackend(delegate, new LoggingListener(log, includeTiming)(delegate.monad))

  def apply[F[_], S](
      delegate: StreamBackend[F, S],
      log: Log[F],
      includeTiming: Boolean,
      logResponseBody: Boolean
  ): StreamBackend[F, S] =
    if (logResponseBody) LoggingWithResponseBodyBackend(delegate, log, includeTiming)
    else ListenerBackend(delegate, new LoggingListener(log, includeTiming)(delegate.monad))

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      log: Log[F],
      includeTiming: Boolean,
      logResponseBody: Boolean
  ): WebSocketStreamBackend[F, S] =
    if (logResponseBody) LoggingWithResponseBodyBackend(delegate, log, includeTiming)
    else ListenerBackend(delegate, new LoggingListener(log, includeTiming)(delegate.monad))
}
