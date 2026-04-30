package sttp.client4.http4s

import cats.effect.Async
import fs2.Stream
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.compression.CompressionHandlers

private[http4s] trait Http4sBackendPlatform { self: Http4sBackendCompanion =>

  override def defaultCompressionHandlers[F[_]: Async]: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
    CompressionHandlers(Nil, Nil)
}
