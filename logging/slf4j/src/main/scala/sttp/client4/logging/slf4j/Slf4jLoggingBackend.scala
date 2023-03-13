package sttp.client4.logging.slf4j

import sttp.client4._
import sttp.client4.logging.{LogConfig, Logger, LoggingBackend}
import sttp.monad.MonadError

object Slf4jLoggingBackend {
  def apply(delegate: SyncBackend): SyncBackend =
    LoggingBackend(delegate, logger(delegate.monad))

  def apply[F[_]](delegate: Backend[F]): Backend[F] =
    LoggingBackend(delegate, logger(delegate.monad))

  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] =
    LoggingBackend(delegate, logger(delegate.monad))

  def apply[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] =
    LoggingBackend(delegate, logger(delegate.monad))

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] =
    LoggingBackend(delegate, logger(delegate.monad))

  def apply(delegate: SyncBackend, config: LogConfig): SyncBackend =
    LoggingBackend(delegate, logger(delegate.monad), config)

  def apply[F[_]](delegate: Backend[F], config: LogConfig): Backend[F] =
    LoggingBackend(delegate, logger(delegate.monad), config)

  def apply[F[_]](delegate: WebSocketBackend[F], config: LogConfig): WebSocketBackend[F] =
    LoggingBackend(delegate, logger(delegate.monad), config)

  def apply[F[_], S](delegate: StreamBackend[F, S], config: LogConfig): StreamBackend[F, S] =
    LoggingBackend(delegate, logger(delegate.monad), config)

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S], config: LogConfig): WebSocketStreamBackend[F, S] =
    LoggingBackend(delegate, logger(delegate.monad), config)

  private def logger[F[_]](monad: MonadError[F]): Logger[F] =
    new Slf4jLogger("sttp.client4.logging.slf4j.Slf4jLoggingBackend", monad)
}
