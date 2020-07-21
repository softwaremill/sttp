package sttp.client.asynchttpclient.monix

import java.io.File
import java.nio.ByteBuffer

import cats.effect.Resource
import io.netty.buffer.{ByteBuf, Unpooled}
import monix.eval.Task
import monix.execution.Scheduler
import monix.nio.file._
import monix.reactive.Observable
import org.asynchttpclient._
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, WebSocketHandler}
import sttp.client.impl.monix.{MonixStreams, TaskMonadAsyncError}
import sttp.client.internal._
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}

class AsyncHttpClientMonixBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
)(implicit
    scheduler: Scheduler
) extends AsyncHttpClientBackend[Task, MonixStreams, MonixStreams](
      asyncHttpClient,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override val streams: MonixStreams = MonixStreams

  override protected def streamBodyToPublisher(s: Observable[ByteBuffer]): Publisher[ByteBuf] =
    s.map(Unpooled.wrappedBuffer).toReactivePublisher

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Observable[ByteBuffer] =
    Observable.fromReactivePublisher(p)

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): Task[Array[Byte]] = {
    val bytes = Observable
      .fromReactivePublisher(p)
      .foldLeftL(ByteBuffer.allocate(0))(concatByteBuffers)

    bytes.map(_.array())
  }

  override protected def publisherToFile(p: Publisher[ByteBuffer], f: File): Task[Unit] = {
    Observable
      .fromReactivePublisher(p)
      .map(_.array())
      .consumeWith(writeAsync(f.toPath))
      .map(_ => ())
  }
}

object AsyncHttpClientMonixBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  )(implicit
      scheduler: Scheduler
  ): SttpBackend[Task, MonixStreams, WebSocketHandler] =
    new FollowRedirectsBackend(new AsyncHttpClientMonixBackend(asyncHttpClient, closeClient, customizeRequest))

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, MonixStreams, WebSocketHandler]] =
    Task.eval(
      AsyncHttpClientMonixBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
    )

  /**
    * Makes sure the backend is closed after usage.
    */
  def resource(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams, WebSocketHandler]] =
    Resource.make(apply(options, customizeRequest))(_.close())

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, MonixStreams, WebSocketHandler]] =
    Task.eval(AsyncHttpClientMonixBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /**
    * Makes sure the backend is closed after usage.
    */
  def resourceUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams, WebSocketHandler]] =
    Resource.make(usingConfig(cfg, customizeRequest))(_.close())

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, MonixStreams, WebSocketHandler]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  /**
    * Makes sure the backend is closed after usage.
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def resourceUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams, WebSocketHandler]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest))(_.close())

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit s: Scheduler = Scheduler.global): SttpBackend[Task, MonixStreams, WebSocketHandler] =
    AsyncHttpClientMonixBackend(client, closeClient = false, customizeRequest)

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, MonixStreams, WebSocketHandler] = SttpBackendStub(TaskMonadAsyncError)
}
