package sttp.client4.armeria.zio

import _root_.zio.interop.reactivestreams.{
  publisherToStream => publisherToZioStream,
  streamToPublisher => zioStreamToPublisher
}
import _root_.zio.{Chunk, Task, _}
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import org.reactivestreams.Publisher
import sttp.capabilities.zio.ZioStreams
import sttp.client4.armeria.ArmeriaWebClient.newClient
import sttp.client4.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.{wrappers, BackendOptions, StreamBackend}
import sttp.monad.MonadAsyncError
import zio.stream.Stream

private final class ArmeriaZioBackend(runtime: Runtime[Any], client: WebClient, closeFactory: Boolean)
    extends AbstractArmeriaBackend[Task, ZioStreams](client, closeFactory, new RIOMonadAsyncError[Any]) {

  override val streams: ZioStreams = ZioStreams

  override protected def bodyFromStreamMessage: BodyFromStreamMessage[Task, ZioStreams] =
    new BodyFromStreamMessage[Task, ZioStreams] {

      override val streams: ZioStreams = ZioStreams

      override implicit def monad: MonadAsyncError[Task] = new RIOMonadAsyncError[Any]

      override def publisherToStream(streamMessage: StreamMessage[HttpData]): Stream[Throwable, Byte] =
        streamMessage.toZIOStream().mapConcatChunk(httpData => Chunk.fromArray(httpData.array()))
    }

  override protected def streamToPublisher(stream: Stream[Throwable, Byte]): Publisher[HttpData] =
    Unsafe.unsafeCompat { implicit u =>
      runtime.unsafe
        .run(stream.mapChunks(c => Chunk.single(HttpData.wrap(c.toArray))).toPublisher)
        .getOrThrowFiberFailure()
    }
}

object ArmeriaZioBackend {

  /** Creates a new Armeria backend, using the given or default `SttpBackendOptions`. Due to these customisations, the
    * client will manage its own connection pool. If you'd like to reuse the default Armeria
    * [[https://armeria.dev/docs/client-factory ClientFactory]] use `.usingDefaultClient`.
    */
  def apply(options: BackendOptions = BackendOptions.Default): Task[StreamBackend[Task, ZioStreams]] =
    ZIO
      .runtime[Any]
      .map(runtime => apply(runtime, newClient(options), closeFactory = true))

  def scoped(
      options: BackendOptions = BackendOptions.Default
  ): ZIO[Scope, Throwable, StreamBackend[Task, ZioStreams]] =
    ZIO.acquireRelease(apply(options))(_.close().ignore)

  def scopedUsingClient(client: WebClient): ZIO[Scope, Throwable, StreamBackend[Task, ZioStreams]] =
    ZIO.acquireRelease(
      ZIO
        .runtime[Any]
        .map(runtime => apply(runtime, client, closeFactory = true))
    )(_.close().ignore)

  def layer(options: BackendOptions = BackendOptions.Default): Layer[Throwable, SttpClient] =
    ZLayer.scoped(scoped(options))

  def usingClient(client: WebClient): Task[StreamBackend[Task, ZioStreams]] =
    ZIO
      .runtime[Any]
      .map(runtime => apply(runtime, client, closeFactory = false))

  def usingClient[R](runtime: Runtime[R], client: WebClient): StreamBackend[Task, ZioStreams] =
    apply(runtime, client, closeFactory = false)

  def usingDefaultClient(): Task[StreamBackend[Task, ZioStreams]] =
    ZIO
      .runtime[Any]
      .map(runtime => apply(runtime, newClient(), closeFactory = false))

  private def apply[R](runtime: Runtime[R], client: WebClient, closeFactory: Boolean): StreamBackend[Task, ZioStreams] =
    wrappers.FollowRedirectsBackend(new ArmeriaZioBackend(runtime, client, closeFactory))
}