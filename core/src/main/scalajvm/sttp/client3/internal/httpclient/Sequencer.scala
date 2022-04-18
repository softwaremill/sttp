package sttp.client3.internal.httpclient

/** Ensures that given effects are always run in sequence. */
private[client3] trait Sequencer[F[_]] {
  def apply[T](t: => F[T]): F[T]
}
