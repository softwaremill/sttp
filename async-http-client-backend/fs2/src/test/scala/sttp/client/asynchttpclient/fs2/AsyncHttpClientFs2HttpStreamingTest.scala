package sttp.client.asynchttpclient.fs2

import cats.effect.{ContextShift, IO}
import fs2.Stream
import sttp.client.impl.fs2.Fs2StreamingTest
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext

class AsyncHttpClientFs2HttpStreamingTest extends Fs2StreamingTest {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  override implicit val backend: SttpBackend[IO, Stream[IO, Byte], NothingT] =
    AsyncHttpClientFs2Backend[IO]().unsafeRunSync()
}
