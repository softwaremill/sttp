package sttp.client3.logging.scribe

import sttp.client3.logging.Logger
import sttp.monad.MonadError

case class ScribeLogger[F[_]](monad: MonadError[F]) extends Logger[F] {
  private implicit def eval(t: => Unit): F[Unit] = monad.eval(t)

  override def debug(message: => String): F[Unit] = scribe.debug(message)

  override def debug(message: => String, t: Throwable): F[Unit] = scribe.debug(message, t)

  override def info(message: => String): F[Unit] = scribe.info(message)

  override def info(message: => String, t: Throwable): F[Unit] = scribe.info(message, t)

  override def warn(message: => String): F[Unit] = scribe.warn(message)

  override def warn(message: => String, t: Throwable): F[Unit] = scribe.warn(message, t)

  override def error(message: => String): F[Unit] = scribe.error(message)

  override def error(message: => String, t: Throwable): F[Unit] = scribe.error(message, t)
}
