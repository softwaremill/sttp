package sttp.client3.armeria.fs2

import cats.effect.{ConcurrentEffect, Resource, Sync}
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import fs2.interop.reactivestreams._
import fs2.{Chunk, Stream}
import org.reactivestreams.Publisher
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.armeria.AbstractArmeriaBackend.newClient
import sttp.client3.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadAsyncError

private final class ArmeriaFs2Backend[F[_]: ConcurrentEffect](client: WebClient, closeFactory: Boolean)
    extends AbstractArmeriaBackend[F, Fs2Streams[F]](client, closeFactory, new CatsMonadAsyncError) {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

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

  /** Creates a new `SttpBackend`. */
  def apply[F[_]: ConcurrentEffect](): SttpBackend[F, Fs2Streams[F]] =
    apply(newClient(), closeFactory = false)

  /** Creates a new `SttpBackend` with the specified `SttpBackendOptions`. */
  def apply[F[_]: ConcurrentEffect](options: SttpBackendOptions): SttpBackend[F, Fs2Streams[F]] =
    apply(newClient(options), closeFactory = true)

  /** Creates a new `SttpBackend` with the specified `SttpBackendOptions`. */
  def resource[F[_]: ConcurrentEffect](options: SttpBackendOptions): Resource[F, SttpBackend[F, Fs2Streams[F]]] = {
    Resource.make(Sync[F].delay(apply(newClient(options), closeFactory = true)))(_.close())
  }

  /** Creates a new `SttpBackend` with the specified `WebClient`.
    */
  def usingClient[F[_]: ConcurrentEffect](client: WebClient): SttpBackend[F, Fs2Streams[F]] =
    apply(client, closeFactory = false)

  private def apply[F[_]: ConcurrentEffect](
      client: WebClient,
      closeFactory: Boolean
  ): SttpBackend[F, Fs2Streams[F]] =
    new FollowRedirectsBackend(new ArmeriaFs2Backend(client, closeFactory))
}
