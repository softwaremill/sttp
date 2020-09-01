package sttp.client.asynchttpclient.fs2

import cats.effect.{Blocker, ContextShift, IO}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client.impl.fs2.Fs2StreamingTest
import sttp.client.SttpBackend

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

class AsyncHttpClientFs2HttpStreamingTest extends Fs2StreamingTest {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  override val backend: SttpBackend[IO, Fs2Streams[IO]] =
    AsyncHttpClientFs2Backend[IO](Blocker.liftExecutionContext(global)).unsafeRunSync()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
