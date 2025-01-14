package sttp.client4.logging.slf4j

import org.slf4j.{LoggerFactory, MDC}
import sttp.client4.logging.{LogLevel, Logger}
import sttp.monad.MonadError

class Slf4jLogger[F[_]](name: String, monad: MonadError[F]) extends Logger[F] {
  private val underlying = LoggerFactory.getLogger(name)

  private def setContext(context: Map[String, Any]): Unit =
    context.foreach { case (k, v) =>
      MDC.put(k, v.toString)
    }

  private def clearContext(context: Map[String, Any]): Unit =
    context.keys.foreach { key =>
      MDC.remove(key)
    }

  override def apply(
      level: LogLevel,
      message: => String,
      throwable: Option[Throwable],
      context: Map[String, Any]
  ): F[Unit] =
    monad.eval {
      setContext(context)
      try
        level match {
          case LogLevel.Trace if underlying.isTraceEnabled =>
            throwable match {
              case Some(t) => underlying.trace(message, throwable)
              case None    => underlying.trace(message)
            }

          case LogLevel.Debug if underlying.isDebugEnabled =>
            throwable match {
              case Some(t) => underlying.debug(message, throwable)
              case None    => underlying.debug(message)
            }

          case LogLevel.Info if underlying.isInfoEnabled =>
            throwable match {
              case Some(t) => underlying.info(message, throwable)
              case None    => underlying.info(message)
            }

          case LogLevel.Warn if underlying.isWarnEnabled =>
            throwable match {
              case Some(t) => underlying.warn(message, throwable)
              case None    => underlying.warn(message)
            }

          case LogLevel.Error if underlying.isErrorEnabled =>
            throwable match {
              case Some(t) => underlying.error(message, throwable)
              case None    => underlying.error(message)
            }

          case _ => ()
        }
      finally
        clearContext(context)
    }
}
