package sttp.client.http4s

import cats.effect.{ContextShift, IO}
import sttp.client.{NothingT, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class Http4sHttpTest extends HttpTest[IO] {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(global)

  override implicit val backend: SttpBackend[IO, Nothing, NothingT] = TestHttp4sBackend()
  override implicit val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }

  override protected def supportsRequestTimeout = false
}
