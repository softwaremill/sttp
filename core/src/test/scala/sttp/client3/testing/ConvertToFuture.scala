package sttp.client3.testing

import sttp.client3.Identity

import scala.concurrent.Future
import scala.util.Try

trait ConvertToFuture[F[_]] {
  def toFuture[T](value: F[T]): Future[T]
}

object ConvertToFuture {

  lazy val id: ConvertToFuture[Identity] = new ConvertToFuture[Identity] {
    override def toFuture[T](value: Identity[T]): Future[T] =
      Future.successful(value)
  }

  lazy val future: ConvertToFuture[Future] = new ConvertToFuture[Future] {
    override def toFuture[T](value: Future[T]): Future[T] = value
  }

  lazy val scalaTry: ConvertToFuture[Try] = new ConvertToFuture[Try] {
    override def toFuture[T](value: Try[T]): Future[T] = Future.fromTry(value)
  }
}
