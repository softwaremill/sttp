package sttp.client3.impl

import _root_.cats.effect.IO
import sttp.client3.testing.ConvertToFuture

import scala.concurrent.Future

package object cats {

  val convertCatsIOToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }
}
