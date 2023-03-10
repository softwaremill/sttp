package sttp.client4.logging.scribe

import sttp.client4._
import sttp.client4.logging.{LogConfig, LoggingBackend}

object ScribeLoggingBackend {
  def apply(delegate: SyncBackend): SyncBackend =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad))

  def apply[F[_]](delegate: Backend[F]): Backend[F] =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad))

  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad))

  def apply[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad))

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad))

  def apply(delegate: SyncBackend, config: LogConfig): SyncBackend =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad), config)

  def apply[F[_]](delegate: Backend[F], config: LogConfig): Backend[F] =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad), config)

  def apply[F[_]](delegate: WebSocketBackend[F], config: LogConfig): WebSocketBackend[F] =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad), config)

  def apply[F[_], S](delegate: StreamBackend[F, S], config: LogConfig): StreamBackend[F, S] =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad), config)

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S], config: LogConfig): WebSocketStreamBackend[F, S] =
    LoggingBackend(delegate, ScribeLogger(delegate.responseMonad), config)
}
