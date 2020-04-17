package sttp.client.asynchttpclient.fs2

import java.nio.ByteBuffer

import cats.effect.{ContextShift, IO}
import fs2.Stream
import sttp.client.impl.fs2.Fs2ByteBufferStreamingTest
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext

class AsyncHttpClientFs2HttpStreamingTest extends Fs2ByteBufferStreamingTest {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  override implicit val backend: SttpBackend[IO, Stream[IO, ByteBuffer], NothingT] =
    AsyncHttpClientFs2Backend[IO]().unsafeRunSync()
}
