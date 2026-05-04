package sttp.client4.http4s

import cats.effect.{Async, Resource}
import fs2.Stream
import fs2.io.net.Network
import org.http4s.{EntityBody, Request => Http4sRequest}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.testing.StreamBackendStub
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.compression.CompressionHandlers

private[http4s] trait Http4sBackendCompanion {

  def defaultCompressionHandlers[F[_]: Async]: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]]

  def usingClient[F[_]: Async: Network](
      client: Client[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): StreamBackend[F, Fs2Streams[F]] =
    FollowRedirectsBackend(new Http4sBackend[F](client, customizeRequest, compressionHandlers(implicitly)))

  def usingEmberClientBuilder[F[_]: Async: Network](
      emberClientBuilder: EmberClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    emberClientBuilder.build.map(c => usingClient(c, customizeRequest, compressionHandlers))

  def usingDefaultEmberClientBuilder[F[_]: Async: Network](
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    usingEmberClientBuilder(EmberClientBuilder.default[F], customizeRequest, compressionHandlers)

  /** Create a stub backend for testing, which uses the `F` response wrapper, and supports `Stream[F, Byte]` streaming.
    *
    * See [[StreamBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: StreamBackendStub[F, Fs2Streams[F]] = StreamBackendStub(new CatsMonadAsyncError)
}
