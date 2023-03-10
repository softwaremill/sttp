package sttp.client4.asynchttpclient.scalaz

import java.nio.ByteBuffer

import io.netty.buffer.ByteBuf
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import scalaz.concurrent.Task
import sttp.client4.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client4.impl.scalaz.TaskMonadAsyncError
import sttp.client4.internal.NoStreams
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.BackendStub
import sttp.client4.{FollowRedirectsBackend, Backend, BackendOptions}
import sttp.monad.MonadAsyncError
import sttp.ws.WebSocket

class AsyncHttpClientScalazBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[Task, Nothing, Any](
      asyncHttpClient,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override val streams: NoStreams = NoStreams

  override protected val bodyFromAHC: BodyFromAHC[Task, Nothing] = new BodyFromAHC[Task, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadAsyncError[Task] = TaskMonadAsyncError
    override def publisherToStream(p: Publisher[ByteBuffer]): Nothing =
      throw new IllegalStateException("This backend does not support streaming")
    override def compileWebSocketPipe(ws: WebSocket[Task], pipe: Nothing): Task[Unit] = pipe // nothing is everything
  }

  override protected def bodyToAHC: BodyToAHC[Task, Nothing] =
    new BodyToAHC[Task, Nothing] {
      override val streams: NoStreams = NoStreams
      override protected def streamToPublisher(s: Nothing): Publisher[ByteBuf] = s // nothing is everything
    }

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    throw new IllegalStateException("Web sockets are not supported!")
}

object AsyncHttpClientScalazBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  ): Backend[Task] =
    FollowRedirectsBackend(new AsyncHttpClientScalazBackend(asyncHttpClient, closeClient, customizeRequest))

  def apply(
             options: BackendOptions = BackendOptions.Default,
             customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[Backend[Task]] =
    Task.delay(
      AsyncHttpClientScalazBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
    )

  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[Backend[Task]] =
    Task.delay(AsyncHttpClientScalazBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
                          updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
                          options: BackendOptions = BackendOptions.Default,
                          customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[Backend[Task]] =
    Task.delay(
      AsyncHttpClientScalazBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Backend[Task] =
    AsyncHttpClientScalazBackend(client, closeClient = false, customizeRequest)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and doesn't support streaming.
    *
    * See [[BackendStub]] for details on how to configure stub responses.
    */
  def stub: BackendStub[Task] = BackendStub(TaskMonadAsyncError)
}
