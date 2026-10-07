package sttp.client4.http4s

import cats.effect.{Async, Resource}
import fs2.Stream
import org.http4s.{EntityBody, Request => Http4sRequest}
import org.http4s.blaze.client.BlazeClientBuilder
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

  private def defaultCompressionHandlersFor[F[_]](F: Async[F]): CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] = {
    implicit val ev: Async[F] = F
    defaultCompressionHandlers[F]
  }

  def usingClient[F[_]: Async](
      client: Client[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]](_)
  ): StreamBackend[F, Fs2Streams[F]] =
    Http4sBackendBase.usingClient(client, customizeRequest, defaultCompressionHandlersFor[F](_))

  def usingClient[F[_]: Async](
      client: Client[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F],
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]]
  ): StreamBackend[F, Fs2Streams[F]] =
    Http4sBackendBase.usingClient(client, customizeRequest, compressionHandlers)

  def usingBlazeClientBuilder[F[_]: Async](
      blazeClientBuilder: BlazeClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]](_)
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    usingBlazeClientBuilder(blazeClientBuilder, customizeRequest, defaultCompressionHandlersFor[F](_))

  def usingBlazeClientBuilder[F[_]: Async](
      blazeClientBuilder: BlazeClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F],
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]]
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    blazeClientBuilder.resource.map(c => usingClient(c, customizeRequest, compressionHandlers))

  def usingDefaultBlazeClientBuilder[F[_]: Async](
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]](_),
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]] =
        defaultCompressionHandlersFor[F](_)
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    usingBlazeClientBuilder(BlazeClientBuilder[F], customizeRequest, compressionHandlers)

  def usingEmberClientBuilder[F[_]: Async](
      emberClientBuilder: EmberClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]](_)
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    usingEmberClientBuilder(emberClientBuilder, customizeRequest, defaultCompressionHandlersFor[F](_))

  def usingEmberClientBuilder[F[_]: Async](
      emberClientBuilder: EmberClientBuilder[F],
      customizeRequest: Http4sRequest[F] => Http4sRequest[F],
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]]
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    emberClientBuilder.build.map(c => usingClient(c, customizeRequest, compressionHandlers))

  def usingDefaultEmberClientBuilder[F[_]: Async](
      customizeRequest: Http4sRequest[F] => Http4sRequest[F] = identity[Http4sRequest[F]](_),
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], EntityBody[F]] =
        defaultCompressionHandlersFor[F](_)
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    usingEmberClientBuilder(EmberClientBuilder.default[F], customizeRequest, compressionHandlers)

  /** Create a stub backend for testing, which uses the `F` response wrapper, and supports `Stream[F, Byte]` streaming.
    *
    * See [[StreamBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: StreamBackendStub[F, Fs2Streams[F]] = Http4sBackendBase.stub[F]
}
