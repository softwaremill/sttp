package com.softwaremill.sttp.impl

import _root_.cats.effect.IO
import com.softwaremill.sttp.testing.ConvertToFuture

import scala.concurrent.Future

package object cats {

  val convertCatsIOToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }
}
