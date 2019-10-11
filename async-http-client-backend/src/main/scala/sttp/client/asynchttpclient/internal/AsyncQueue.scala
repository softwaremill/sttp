package sttp.client.asynchttpclient.internal

trait AsyncQueue[F[_], T] {
  def clear(): Unit
  def offer(t: T): Unit
  def poll: F[T]
}
