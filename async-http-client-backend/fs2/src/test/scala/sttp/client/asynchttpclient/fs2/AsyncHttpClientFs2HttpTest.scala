package sttp.client.asynchttpclient.fs2

import cats.effect.{ContextShift, IO}
import sttp.client.{NothingT, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.{ExecutionContext, Future}

class AsyncHttpClientFs2HttpTest extends HttpTest[IO] {

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  override implicit val backend: SttpBackend[IO, Nothing, NothingT] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()
  override implicit val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }
}
