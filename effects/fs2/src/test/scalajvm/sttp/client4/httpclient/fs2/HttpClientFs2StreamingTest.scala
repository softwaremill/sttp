package sttp.client4.httpclient.fs2

import cats.effect.IO
import cats.effect.kernel.Deferred
import fs2.Stream
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.impl.fs2.Fs2StreamingTest
import sttp.client4.testing.HttpTest.endpoint

import scala.concurrent.duration._

class HttpClientFs2StreamingTest extends Fs2StreamingTest with HttpClientFs2TestBase {
  "cancel a response stream while waiting for data" in {
    // the endpoint sends one chunk and then nothing, so the idle timeout cancels the stream while it waits for data;
    // this used to hang, see #2963
    val request: StreamRequest[Stream[IO, Byte], Fs2Streams[IO]] =
      basicRequest.get(uri"$endpoint/streaming/stall").response(asStreamAlwaysUnsafe(streams))

    request
      .send(backend)
      .flatMap(response => bodyConsumer(response.body.timeoutOnPullTo(500.millis, Stream.empty)))
      .timeoutAndForget(10.seconds)
      .map(_ shouldBe "a")
      .toFuture()
  }

  "cancel a request while streaming the request body" in {
    (for {
      started <- Deferred[IO, Unit]
      finalized <- Deferred[IO, Unit]
      body = (Stream.emit('a'.toByte) ++ Stream.exec(started.complete(()).void) ++ Stream.never[IO])
        .onFinalize(finalized.complete(()).void)
      sending <- basicRequest
        .post(uri"$endpoint/streaming/echo")
        .streamBody(streams)(body)
        .send(backend)
        .start
      _ <- started.get.timeout(5.seconds)
      _ <- sending.cancel.timeout(5.seconds)
      _ <- finalized.get.timeout(5.seconds)
    } yield succeed).toFuture()
  }
}
