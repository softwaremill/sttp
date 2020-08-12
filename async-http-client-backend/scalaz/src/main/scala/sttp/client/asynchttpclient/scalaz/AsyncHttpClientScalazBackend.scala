package sttp.client.asynchttpclient.scalaz

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
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client.impl.scalaz.TaskMonadAsyncError
import sttp.client.internal.NoStreams
import sttp.client.internal.ws.SimpleQueue
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
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
  ): SttpBackend[Task, Any] =
    new FollowRedirectsBackend(new AsyncHttpClientScalazBackend(asyncHttpClient, closeClient, customizeRequest))

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Any]] =
    Task.delay(
      AsyncHttpClientScalazBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
    )

  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Any]] =
    Task.delay(AsyncHttpClientScalazBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Any]] =
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
  ): SttpBackend[Task, Any] =
    AsyncHttpClientScalazBackend(client, closeClient = false, customizeRequest)

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, Any] = SttpBackendStub(TaskMonadAsyncError)
}
