package com.softwaremill.sttp.impl

import _root_.cats.effect.IO
import com.softwaremill.sttp.testing.streaming.ConvertToFuture

import scala.concurrent.Future

package object cats {

  val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }
}
