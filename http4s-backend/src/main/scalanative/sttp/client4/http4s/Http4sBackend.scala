package sttp.client4.http4s

import cats.effect.{Async, Resource}
import fs2.Stream
import fs2.io.net.Network
import org.http4s.{EntityBody, Request => Http4sRequest}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
import sttp.client4.compression.CompressionHandlers
import sttp.client4.testing.StreamBackendStub

class Http4sBackend[F[_]: Async](
    protected val client: Client[F],
    protected val customizeRequest: Http4sRequest[F] => Http4sRequest[F],
    protected val compressionHandlers: CompressionHandlers[Fs2Streams[F], EntityBody[F]]
) extends Http4sBackendBase[F]

object Http4sBackend {

  def defaultCompressionHandlers[F[_]: Async]: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
    Http4sBackendBase.defaultCompressionHandlers[F]

  def usingClient[F[_]: Async](
      client: Client[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _
  ): StreamBackend[F, Fs2Streams[F]] =
    Http4sBackendBase.usingClient(client, customizeRequest, defaultCompressionHandlers[F](_: Async[F]))

  def usingClient[F[_]: Async](
      client: Client[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F],
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]]
  ): StreamBackend[F, Fs2Streams[F]] =
    Http4sBackendBase.usingClient(client, customizeRequest, compressionHandlers)

  def usingEmberClientBuilder[F[_]: Async: Network](
      emberClientBuilder: EmberClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]] _
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    usingEmberClientBuilder(emberClientBuilder, customizeRequest, defaultCompressionHandlers[F](_: Async[F]))

  def usingEmberClientBuilder[F[_]: Async: Network](
      emberClientBuilder: EmberClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F],
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]]
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
  def stub[F[_]: Async]: StreamBackendStub[F, Fs2Streams[F]] = Http4sBackendBase.stub[F]
}
