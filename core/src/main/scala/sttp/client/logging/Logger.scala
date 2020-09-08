package sttp.client.logging

/**
  * Interfaces with a logger system.
  */
trait Logger[F[_]] {
  def debug(message: => String): F[Unit]
  def debug(message: => String, t: Throwable): F[Unit]

  def info(message: => String): F[Unit]
  def info(message: => String, t: Throwable): F[Unit]

  def warn(message: => String): F[Unit]
  def warn(message: => String, t: Throwable): F[Unit]

  def error(message: => String): F[Unit]
  def error(message: => String, t: Throwable): F[Unit]
}
