package sttp.client4.http4s

import cats.effect.{Async, Resource}
import fs2.Stream
import org.http4s.{EntityBody, Request => Http4sRequest}
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
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

  def usingBlazeClientBuilder[F[_]: Async](
      blazeClientBuilder: BlazeClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    blazeClientBuilder.resource.map(c => usingClient(c, customizeRequest, compressionHandlers))

  def usingDefaultBlazeClientBuilder[F[_]: Async](
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    usingBlazeClientBuilder(
      BlazeClientBuilder[F],
      customizeRequest,
      compressionHandlers
    )
}
