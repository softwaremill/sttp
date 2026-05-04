package sttp.client4.http4s

import cats.effect.Async
import fs2.Stream
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.compression.CompressionHandlers
import sttp.client4.impl.fs2.PlatformGZipFs2Compressor
import sttp.client4.impl.fs2.PlatformDeflateFs2Compressor
import sttp.client4.impl.fs2.GZipFs2Decompressor
import sttp.client4.impl.fs2.DeflateFs2Decompressor

private[http4s] trait Http4sBackendPlatform { self: Http4sBackendCompanion =>

  override def defaultCompressionHandlers[F[_]: Async]: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
    CompressionHandlers(
      List(new PlatformGZipFs2Compressor[F, Fs2Streams[F]] {}, new PlatformDeflateFs2Compressor[F, Fs2Streams[F]] {}),
      List(new GZipFs2Decompressor, new DeflateFs2Decompressor)
    )
}
