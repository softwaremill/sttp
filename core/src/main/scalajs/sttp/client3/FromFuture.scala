package sttp.client3

import scala.concurrent.Future

trait FromFuture[F[_]] {
  def apply[T](f: Future[T]): F[T]
}
