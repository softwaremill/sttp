package sttp.client4.logging.scribe

import scribe.data
import scribe.mdc.MDC
import sttp.client4.logging.{LogLevel, Logger}
import sttp.monad.MonadError

case class ScribeLogger[F[_]](monad: MonadError[F]) extends Logger[F] {
  private val levelMap: Map[LogLevel, scribe.Level] = Map(
    LogLevel.Trace -> scribe.Level.Trace,
    LogLevel.Debug -> scribe.Level.Debug,
    LogLevel.Info -> scribe.Level.Info,
    LogLevel.Warn -> scribe.Level.Warn,
    LogLevel.Error -> scribe.Level.Error
  )

  override def apply(level: LogLevel, message: => String, context: Map[String, Any]): F[Unit] = monad.eval(
    scribe.log(levelMap(level), MDC.global, message, data(context))
  )

  override def apply(level: LogLevel, message: => String, throwable: Throwable, context: Map[String, Any]): F[Unit] =
    monad.eval(
      scribe.log(levelMap(level), MDC.global, message, data(context), throwable)
    )
}
