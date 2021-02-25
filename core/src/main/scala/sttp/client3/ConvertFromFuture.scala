package sttp.client3

import scala.concurrent.Future

trait ConvertFromFuture[F[_]] {
  def fromFuture[T](f: Future[T]): F[T]
}

object ConvertFromFuture {
  lazy val future: ConvertFromFuture[Future] = new ConvertFromFuture[Future] {
    override def fromFuture[T](f: Future[T]): Future[T] = f
  }
}
