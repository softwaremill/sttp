package sttp.client4.caching

import scala.concurrent.duration.FiniteDuration

/** A cache interface to be used with [[CachingBackend]].
  *
  * @tparam f
  *   The effect type, [[sttp.shared.Identity]] for direct-style (synchronous). Must be the same as used by the backend,
  *   which is being wrapped.
  */
trait Cache[F[_]] {
  def get(key: Array[Byte]): F[Option[Array[Byte]]]
  def delete(key: Array[Byte]): F[Unit]
  def set(key: Array[Byte], value: Array[Byte], ttl: FiniteDuration): F[Unit]
  def close(): F[Unit]
}
