package sttp.client4.logging

/** Interfaces with a logger system. */
trait Logger[F[_]] {
  def apply(level: LogLevel, message: => String, exception: Option[Throwable], context: Map[String, Any]): F[Unit]
}

sealed trait LogLevel
object LogLevel {
  case object Trace extends LogLevel
  case object Debug extends LogLevel
  case object Info extends LogLevel
  case object Warn extends LogLevel
  case object Error extends LogLevel
}
