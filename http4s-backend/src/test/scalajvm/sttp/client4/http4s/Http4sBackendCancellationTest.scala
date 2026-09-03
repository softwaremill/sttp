package sttp.client4.http4s

import cats.effect.{Deferred, IO}
import cats.effect.unsafe.IORuntime
import org.http4s.{Response => Http4sResponse}
import org.http4s.client.Client
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._

import scala.concurrent.duration._

class Http4sBackendCancellationTest extends AsyncFlatSpec with Matchers {

  implicit val ioRuntime: IORuntime = IORuntime.global

  it should "cancel the underlying request fiber when the caller cancels" in {
    val test = for {
      cancelled <- Deferred[IO, Unit]
      // A client whose request never completes, but records when it is cancelled
      client = Client[IO] { _ =>
        IO.never[Http4sResponse[IO]]
          .onCancel(cancelled.complete(()).void)
          .toResource
      }
      backend = Http4sBackend.usingClient[IO](client)
      req = basicRequest.get(uri"http://localhost/test").response(asString)
      // Send the request, then cancel it after a short delay
      _ <- req.send(backend).void.timeoutTo(50.millis, IO.unit)
      // If the fiber was properly cancelled, onCancel will have signalled
      _ <- cancelled.get.timeout(3.seconds)
    } yield succeed

    test.unsafeToFuture()
  }
}
