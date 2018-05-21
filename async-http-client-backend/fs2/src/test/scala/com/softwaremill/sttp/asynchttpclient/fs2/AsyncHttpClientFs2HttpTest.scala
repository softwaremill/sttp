package com.softwaremill.sttp.asynchttpclient.fs2

import cats.effect.IO
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AsyncHttpClientFs2HttpTest extends HttpTest[IO] {

  override implicit val backend: SttpBackend[IO, Nothing] = AsyncHttpClientFs2Backend()
  override implicit val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }
}
