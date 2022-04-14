package sttp.client3.httpclient

/** Ensures that given effects are always run in sequence. */
private[httpclient] trait Sequencer[F[_]] {
  def apply[T](t: => F[T]): F[T]
}
