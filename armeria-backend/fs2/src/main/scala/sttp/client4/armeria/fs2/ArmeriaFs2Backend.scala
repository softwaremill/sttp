package sttp.client4.armeria.fs2

import cats.effect.std.Dispatcher
import cats.effect.kernel.{Async, Resource, Sync}
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import fs2.interop.reactivestreams._
import fs2.{Chunk, Stream}
import org.reactivestreams.Publisher
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.armeria.ArmeriaWebClient.newClient
import sttp.client4.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.{BackendOptions, StreamBackend, wrappers}
import sttp.monad.MonadAsyncError

private final class ArmeriaFs2Backend[F[_]: Async](client: WebClient, closeFactory: Boolean, dispatcher: Dispatcher[F])
    extends AbstractArmeriaBackend[F, Fs2Streams[F]](client, closeFactory, new CatsMonadAsyncError) {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override protected def bodyFromStreamMessage: BodyFromStreamMessage[F, Fs2Streams[F]] =
    new BodyFromStreamMessage[F, Fs2Streams[F]] {

      override val streams: Fs2Streams[F] = Fs2Streams[F]

      override implicit val monad: MonadAsyncError[F] = new CatsMonadAsyncError

      override def publisherToStream(streamMessage: StreamMessage[HttpData]): Stream[F, Byte] =
        streamMessage.toStream[F].flatMap(httpData => Stream.chunk(Chunk.array(httpData.array())))
    }

  override protected def streamToPublisher(stream: Stream[F, Byte]): Publisher[HttpData] =
    StreamUnicastPublisher(
      stream.chunks
        .map { chunk =>
          val bytes = chunk.compact
          HttpData.wrap(bytes.values, bytes.offset, bytes.length)
        },
      dispatcher
    )
}

object ArmeriaFs2Backend {

  /** Creates a new Armeria backend, using the given or default `SttpBackendOptions`. Due to these customisations, the
    * client will manage its own connection pool. If you'd like to reuse the default Armeria
    * [[https://armeria.dev/docs/client-factory ClientFactory]] use `.usingDefaultClient`.
    */
  def apply[F[_]: Async](
      dispatcher: Dispatcher[F],
      options: BackendOptions = BackendOptions.Default
  ): StreamBackend[F, Fs2Streams[F]] =
    apply(newClient(options), closeFactory = true, dispatcher)

  def resource[F[_]: Async](
      options: BackendOptions = BackendOptions.Default
  ): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher =>
        Resource.make(Sync[F].delay(apply(newClient(options), closeFactory = true, dispatcher)))(_.close())
      )

  def resourceUsingClient[F[_]: Async](client: WebClient): Resource[F, StreamBackend[F, Fs2Streams[F]]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher => Resource.make(Sync[F].delay(apply(client, closeFactory = true, dispatcher)))(_.close()))

  def usingClient[F[_]: Async](client: WebClient, dispatcher: Dispatcher[F]): StreamBackend[F, Fs2Streams[F]] =
    apply(client, closeFactory = false, dispatcher)

  def usingDefaultClient[F[_]: Async](dispatcher: Dispatcher[F]): StreamBackend[F, Fs2Streams[F]] =
    apply(newClient(), closeFactory = false, dispatcher)

  private def apply[F[_]: Async](
      client: WebClient,
      closeFactory: Boolean,
      dispatcher: Dispatcher[F]
  ): StreamBackend[F, Fs2Streams[F]] =
    wrappers.FollowRedirectsBackend(new ArmeriaFs2Backend(client, closeFactory, dispatcher))
}
