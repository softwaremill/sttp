package sttp.client3.armeria.fs2

import cats.effect.{ConcurrentEffect, Resource, Sync, ExitCase}
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import fs2.interop.reactivestreams._
import fs2.{Chunk, Stream}
import org.reactivestreams.Publisher
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.armeria.ArmeriaWebClient.newClient
import sttp.client3.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadAsyncError

private final class ArmeriaFs2Backend[F[_]: ConcurrentEffect](client: WebClient, closeFactory: Boolean)
    extends AbstractArmeriaBackend[F, Fs2Streams[F]](client, closeFactory, new CatsMonadAsyncError) {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override protected def ensureOnAbnormal[T](effect: F[T])(finalizer: => F[Unit]): F[T] =
    ConcurrentEffect[F].guaranteeCase(effect) { exitCase =>
      if (exitCase == ExitCase.Completed) ConcurrentEffect[F].unit
      else ConcurrentEffect[F].onError(finalizer) { case t => ConcurrentEffect[F].delay(t.printStackTrace()) }
    }

  override protected def bodyFromStreamMessage: BodyFromStreamMessage[F, Fs2Streams[F]] =
    new BodyFromStreamMessage[F, Fs2Streams[F]] {

      override val streams: Fs2Streams[F] = Fs2Streams[F]

      override implicit val monad: MonadAsyncError[F] = new CatsMonadAsyncError

      override def publisherToStream(streamMessage: StreamMessage[HttpData]): Stream[F, Byte] =
        streamMessage.toStream[F].flatMap(httpData => Stream.chunk(Chunk.bytes(httpData.array())))
    }

  override protected def streamToPublisher(stream: Stream[F, Byte]): Publisher[HttpData] =
    stream.chunks
      .map(chunk => {
        val bytes = chunk.toBytes
        HttpData.wrap(bytes.values, bytes.offset, bytes.length)
      })
      .toUnicastPublisher
}

object ArmeriaFs2Backend {

  /** Creates a new Armeria backend, using the given or default `SttpBackendOptions`. Due to these customisations, the
    * client will manage its own connection pool. If you'd like to reuse the default Armeria
    * [[https://armeria.dev/docs/client-factory ClientFactory]] use `.usingDefaultClient`.
    */
  def apply[F[_]: ConcurrentEffect](
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): SttpBackend[F, Fs2Streams[F]] =
    apply(newClient(options), closeFactory = true)

  def resource[F[_]: ConcurrentEffect](
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): Resource[F, SttpBackend[F, Fs2Streams[F]]] = {
    Resource.make(Sync[F].delay(apply(newClient(options), closeFactory = true)))(_.close())
  }

  def resourceUsingClient[F[_]: ConcurrentEffect](client: WebClient): Resource[F, SttpBackend[F, Fs2Streams[F]]] = {
    Resource.make(Sync[F].delay(apply(client, closeFactory = true)))(_.close())
  }

  def usingClient[F[_]: ConcurrentEffect](client: WebClient): SttpBackend[F, Fs2Streams[F]] =
    apply(client, closeFactory = false)

  def usingDefaultClient[F[_]: ConcurrentEffect](): SttpBackend[F, Fs2Streams[F]] =
    apply(newClient(), closeFactory = false)

  private def apply[F[_]: ConcurrentEffect](
      client: WebClient,
      closeFactory: Boolean
  ): SttpBackend[F, Fs2Streams[F]] =
    new FollowRedirectsBackend(new ArmeriaFs2Backend(client, closeFactory))
}
