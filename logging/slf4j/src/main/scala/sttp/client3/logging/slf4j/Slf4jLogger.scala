package sttp.client3.logging.slf4j

import org.slf4j.LoggerFactory
import sttp.client3.logging.{LogLevel, Logger}
import sttp.monad.MonadError

class Slf4jLogger[F[_]](name: String, monad: MonadError[F]) extends Logger[F] {
  private val underlying = LoggerFactory.getLogger(name)

  override def apply(level: LogLevel, message: => String): F[Unit] = monad.eval {
    level match {
      case LogLevel.Trace => if (underlying.isTraceEnabled) underlying.trace(message) else ()
      case LogLevel.Debug => if (underlying.isDebugEnabled) underlying.debug(message) else ()
      case LogLevel.Info  => if (underlying.isInfoEnabled) underlying.info(message) else ()
      case LogLevel.Warn  => if (underlying.isWarnEnabled) underlying.warn(message) else ()
      case LogLevel.Error => if (underlying.isErrorEnabled) underlying.error(message) else ()
    }
  }

  override def apply(level: LogLevel, message: => String, t: Throwable): F[Unit] = monad.eval {
    level match {
      case LogLevel.Trace => if (underlying.isTraceEnabled) underlying.trace(message, t) else ()
      case LogLevel.Debug => if (underlying.isDebugEnabled) underlying.debug(message, t) else ()
      case LogLevel.Info  => if (underlying.isInfoEnabled) underlying.info(message, t) else ()
      case LogLevel.Warn  => if (underlying.isWarnEnabled) underlying.warn(message, t) else ()
      case LogLevel.Error => if (underlying.isErrorEnabled) underlying.error(message, t) else ()
    }
  }
}
