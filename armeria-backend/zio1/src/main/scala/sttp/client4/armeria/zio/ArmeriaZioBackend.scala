package sttp.client4.armeria.zio

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import org.reactivestreams.Publisher
import sttp.capabilities.zio.ZioStreams
import sttp.client4.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.{wrappers, BackendOptions, StreamBackend}
import sttp.monad.MonadAsyncError
import zio.{Chunk, Task}
import zio.stream.Stream
import _root_.zio._
import _root_.zio.interop.reactivestreams.{
  publisherToStream => publisherToZioStream,
  streamToPublisher => zioStreamToPublisher
}
import sttp.client4.armeria.ArmeriaWebClient.newClient
import sttp.client4.wrappers.FollowRedirectsBackend

private final class ArmeriaZioBackend(runtime: Runtime[Any], client: WebClient, closeFactory: Boolean)
    extends AbstractArmeriaBackend[Task, ZioStreams](client, closeFactory, new RIOMonadAsyncError[Any]) {

  override val streams: ZioStreams = ZioStreams

  override protected def bodyFromStreamMessage: BodyFromStreamMessage[Task, ZioStreams] =
    new BodyFromStreamMessage[Task, ZioStreams] {

      override val streams: ZioStreams = ZioStreams

      override implicit def monad: MonadAsyncError[Task] = new RIOMonadAsyncError[Any]

      override def publisherToStream(streamMessage: StreamMessage[HttpData]): Stream[Throwable, Byte] =
        streamMessage.toStream().mapConcatChunk(httpData => Chunk.fromArray(httpData.array()))
    }

  override protected def streamToPublisher(stream: Stream[Throwable, Byte]): Publisher[HttpData] =
    runtime.unsafeRun(stream.mapChunks(c => Chunk.single(HttpData.wrap(c.toArray))).toPublisher)

  override protected def ensureOnAbnormal[T](effect: Task[T])(finalizer: => Task[Unit]): Task[T] = effect.onExit {
    exit =>
      if (exit.succeeded) ZIO.unit else finalizer.catchAll(t => ZIO.effect(t.printStackTrace()).orDie)
  }.resurrect
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

  def managed(options: BackendOptions = BackendOptions.Default): TaskManaged[StreamBackend[Task, ZioStreams]] =
    ZManaged.make(apply(options))(_.close().ignore)

  def layer(options: BackendOptions = BackendOptions.Default): Layer[Throwable, SttpClient] =
    ZLayer.fromManaged(managed(options))

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
