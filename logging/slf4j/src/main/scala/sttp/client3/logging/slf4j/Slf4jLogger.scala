package sttp.client3.logging.slf4j

import org.slf4j.LoggerFactory
import sttp.client3.logging.Logger
import sttp.monad.MonadError

class Slf4jLogger[F[_]](name: String, monad: MonadError[F]) extends Logger[F] {
  private val underlying = LoggerFactory.getLogger(name)

  def debug(message: => String): F[Unit] =
    monad.eval {
      if (underlying.isDebugEnabled) underlying.debug(message) else ()
    }
  def debug(message: => String, t: Throwable): F[Unit] =
    monad.eval {
      if (underlying.isDebugEnabled) underlying.debug(message, t) else ()
    }

  def info(message: => String): F[Unit] =
    monad.eval {
      if (underlying.isInfoEnabled) underlying.info(message) else ()
    }
  def info(message: => String, t: Throwable): F[Unit] =
    monad.eval {
      if (underlying.isInfoEnabled) underlying.info(message, t) else ()
    }

  def warn(message: => String): F[Unit] =
    monad.eval {
      if (underlying.isWarnEnabled) underlying.warn(message) else ()
    }
  def warn(message: => String, t: Throwable): F[Unit] =
    monad.eval {
      if (underlying.isWarnEnabled) underlying.warn(message, t) else ()
    }

  def error(message: => String): F[Unit] =
    monad.eval {
      if (underlying.isErrorEnabled) underlying.error(message) else ()
    }
  def error(message: => String, t: Throwable): F[Unit] =
    monad.eval {
      if (underlying.isErrorEnabled) underlying.error(message, t) else ()
    }
}
