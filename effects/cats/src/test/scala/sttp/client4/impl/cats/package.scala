package sttp.client4.impl

import _root_.cats.effect.{unsafe, IO}
import sttp.client4.testing.ConvertToFuture

import scala.concurrent.Future

package object cats {

  def convertCatsIOToFuture()(implicit runtime: unsafe.IORuntime): ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }
}
