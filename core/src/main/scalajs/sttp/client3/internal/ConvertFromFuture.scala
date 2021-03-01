package sttp.client3.internal

import scala.concurrent.Future

trait ConvertFromFuture[F[_]] {
  def apply[T](f: Future[T]): F[T]
}

object ConvertFromFuture {
  lazy val future: ConvertFromFuture[Future] = new ConvertFromFuture[Future] {
    override def apply[T](f: Future[T]): Future[T] = f
  }
}
