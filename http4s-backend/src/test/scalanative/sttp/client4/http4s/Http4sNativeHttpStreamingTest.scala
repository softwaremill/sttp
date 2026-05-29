package sttp.client4.http4s

import cats.effect.IO
import sttp.client4.StreamBackend
import sttp.client4.impl.fs2.Fs2StreamingTest
import sttp.capabilities.fs2.Fs2Streams

class Http4sNativeHttpStreamingTest extends Fs2StreamingTest {

  override val backend: StreamBackend[IO, Fs2Streams[IO]] = {
    try {
      Http4sBackend.usingDefaultEmberClientBuilder[IO]().allocated.unsafeRunSync()._1
    } catch {
      case e: Throwable =>
        Console.err.println(s"[Http4sNativeHttpStreamingTest] Failed to create backend: $e ${e.getMessage()} ${e.getCause()}")
        e.printStackTrace(Console.err)
        throw e
    }
  }
}
