package sttp.client.asynchttpclient.monix

import java.io.File
import java.nio.ByteBuffer

import io.netty.buffer.{ByteBuf, Unpooled}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.nio.file._
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, WebSocketHandler}
import sttp.client.impl.monix.TaskMonadAsyncError
import sttp.client.internal._
import sttp.client.ws.WebSocketResponse
import sttp.client.{FollowRedirectsBackend, Response, SttpBackend, SttpBackendOptions, _}

class AsyncHttpClientMonixBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
)(
    implicit scheduler: Scheduler
) extends AsyncHttpClientBackend[Task, Observable[ByteBuffer]](
      asyncHttpClient,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override def send[T](r: Request[T, Observable[ByteBuffer]]): Task[Response[T]] = {
    super.send(r).guarantee(Task.shift)
  }

  override def openWebsocket[T, WS_RESULT](
      r: Request[T, Observable[ByteBuffer]],
      handler: WebSocketHandler[WS_RESULT]
  ): Task[WebSocketResponse[WS_RESULT]] = super.openWebsocket(r, handler).guarantee(Task.shift)

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
  )(
      implicit scheduler: Scheduler
  ): SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] =
    new FollowRedirectsBackend(new AsyncHttpClientMonixBackend(asyncHttpClient, closeClient, customizeRequest))

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(
      implicit s: Scheduler = Scheduler.Implicits.global
  ): Task[SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler]] =
    Task.eval(
      AsyncHttpClientMonixBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
    )

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(
      implicit s: Scheduler = Scheduler.Implicits.global
  ): Task[SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler]] =
    Task.eval(AsyncHttpClientMonixBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(
      implicit s: Scheduler = Scheduler.Implicits.global
  ): Task[SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] =
    AsyncHttpClientMonixBackend(client, closeClient = false, customizeRequest)
}
