package com.softwaremill.sttp.testing

import com.softwaremill.sttp.Id
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

trait ConvertToFuture[R[_]] {
  def toFuture[T](value: R[T]): Future[T]
}

object ConvertToFuture {

  lazy val id: ConvertToFuture[Id] = new ConvertToFuture[Id] {
    override def toFuture[T](value: Id[T]): Future[T] =
      Future.successful(value)
  }

  lazy val future: ConvertToFuture[Future] = new ConvertToFuture[Future] {
    override def toFuture[T](value: Future[T]): Future[T] = value
  }

  lazy val scalaTry: ConvertToFuture[Try] = new ConvertToFuture[Try] {
    override def toFuture[T](value: Try[T]): Future[T] = Future.fromTry(value)
  }
}
