package sttp.client3.logging.scribe

import sttp.client3.logging.{LogLevel, Logger}
import sttp.monad.MonadError

case class ScribeLogger[F[_]](monad: MonadError[F]) extends Logger[F] {
  private implicit def eval(t: => Unit): F[Unit] = monad.eval(t)

  override def apply(level: LogLevel, message: => String): F[Unit] = level match {
    case LogLevel.Trace => scribe.trace(message)
    case LogLevel.Debug => scribe.debug(message)
    case LogLevel.Info  => scribe.info(message)
    case LogLevel.Warn  => scribe.warn(message)
    case LogLevel.Error => scribe.error(message)
  }

  override def apply(level: LogLevel, message: => String, t: Throwable): F[Unit] = level match {
    case LogLevel.Trace => scribe.trace(message, t)
    case LogLevel.Debug => scribe.debug(message, t)
    case LogLevel.Info  => scribe.info(message, t)
    case LogLevel.Warn  => scribe.warn(message, t)
    case LogLevel.Error => scribe.error(message, t)
  }
}
