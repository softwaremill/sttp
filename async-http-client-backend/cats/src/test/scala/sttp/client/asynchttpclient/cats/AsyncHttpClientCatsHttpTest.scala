package sttp.client.asynchttpclient.cats

import java.util.concurrent.TimeoutException

import cats.effect.{ContextShift, IO, Timer}
import sttp.client._
import sttp.client.impl.cats.convertCatsIOToFuture
import sttp.client.testing.{CancelTest, ConvertToFuture, HttpTest}

import scala.concurrent.duration._

class AsyncHttpClientCatsHttpTest extends HttpTest[IO] with CancelTest[IO, Nothing] {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)
  override implicit val backend: SttpBackend[IO, Nothing, NothingT] = AsyncHttpClientCatsBackend[IO]().unsafeRunSync()
  override implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      basicRequest.get(uri"ps://sth.com").send().toFuture().failed.map { e => e shouldBe a[IllegalArgumentException] }
    }
  }

  override def timeoutToNone[T](t: IO[T], timeoutMillis: Int): IO[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .handleErrorWith {
        case _: TimeoutException => IO(None)
        case e                   => throw e
      }
}
