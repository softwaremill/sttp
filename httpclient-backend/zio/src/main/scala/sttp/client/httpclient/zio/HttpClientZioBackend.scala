package sttp.client.httpclient.zio

import java.io.InputStream
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer

import org.reactivestreams.FlowAdapters
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.{BodyFromHttpClient, BodyToHttpClient, HttpClientAsyncBackend, HttpClientBackend}
import sttp.client.impl.zio.{BlockingZioStreams, RIOMonadAsyncError, ZioAsyncQueue, ZioWebSockets}
import sttp.client.monad.MonadError
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocket
import sttp.client.ws.internal.AsyncQueue
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions, WebSockets}
import sttp.model.ws.WebSocketFrame
import zio._
import zio.blocking.Blocking
import _root_.zio.interop.reactivestreams.{streamToPublisher => zioStreamToPublisher}
import zio.stream.{Stream, ZStream, ZTransducer}

class HttpClientZioBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler,
    chunkSize: Int
) extends HttpClientAsyncBackend[BlockingTask, BlockingZioStreams, BlockingZioStreams with WebSockets](
      client,
      new RIOMonadAsyncError[Blocking],
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: BlockingZioStreams = BlockingZioStreams

  override protected val bodyToHttpClient: BodyToHttpClient[BlockingTask, BlockingZioStreams] =
    new BodyToHttpClient[BlockingTask, BlockingZioStreams] {
      override val streams: BlockingZioStreams = BlockingZioStreams
      override implicit def monad: MonadError[BlockingTask] = responseMonad
      override def streamToPublisher(stream: ZStream[Blocking, Throwable, Byte]): BlockingTask[BodyPublisher] = {
        val publisher = stream.mapChunks(byteChunk => Chunk(ByteBuffer.wrap(byteChunk.toArray))).toPublisher
        publisher.map { pub =>
          BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(pub))
        }
      }
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[BlockingTask, BlockingZioStreams] =
    new BodyFromHttpClient[BlockingTask, BlockingZioStreams] {
      override val streams: BlockingZioStreams = BlockingZioStreams
      override implicit def monad: MonadError[BlockingTask] = responseMonad

      override def inputStreamToStream(is: InputStream): ZStream[Blocking, Throwable, Byte] =
        Stream.fromInputStreamEffect(ZIO.succeed(is), chunkSize)

      override def compileWebSocketPipe(
          ws: WebSocket[BlockingTask],
          pipe: ZTransducer[Blocking, Throwable, WebSocketFrame.Data[_], WebSocketFrame]
      ): BlockingTask[Unit] = ZioWebSockets.compilePipe(ws, pipe)
    }

  override protected def createAsyncQueue[T]: BlockingTask[AsyncQueue[BlockingTask, T]] =
    for {
      runtime <- ZIO.runtime[Any]
      queue <- Queue.unbounded[T]
    } yield new ZioAsyncQueue(queue, runtime)
}

object HttpClientZioBackend {

  private val defaultChunkSize = 65536

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler,
      chunkSize: Int
  ): SttpBackend[BlockingTask, BlockingZioStreams with WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientZioBackend(
        client,
        closeClient,
        customizeRequest,
        customEncodingHandler,
        chunkSize
      )
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): Task[SttpBackend[BlockingTask, BlockingZioStreams with WebSockets]] =
    Task.effect(
      HttpClientZioBackend(
        HttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        customEncodingHandler,
        chunkSize
      )
    )

  def managed(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): ZManaged[Blocking, Throwable, SttpBackend[BlockingTask, BlockingZioStreams with WebSockets]] =
    ZManaged.make(apply(options, customizeRequest, customEncodingHandler, chunkSize))(
      _.close().ignore
    )

  def layer(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): ZLayer[Blocking, Throwable, SttpClient] = {
    ZLayer.fromManaged(
      (for {
        backend <- HttpClientZioBackend(
          options,
          customizeRequest,
          customEncodingHandler,
          chunkSize
        )
      } yield backend).toManaged(_.close().ignore)
    )
  }

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): SttpBackend[BlockingTask, BlockingZioStreams with WebSockets] =
    HttpClientZioBackend(
      client,
      closeClient = false,
      customizeRequest,
      customEncodingHandler,
      chunkSize
    )

  def layerUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): ZLayer[Blocking, Throwable, SttpClient] = {
    ZLayer.fromManaged(
      ZManaged
        .makeEffect(
          usingClient(
            client,
            customizeRequest,
            customEncodingHandler,
            chunkSize
          )
        )(_.close().ignore)
    )
  }

  /**
    * Create a stub backend for testing, which uses the [[BlockingTask]] response wrapper, and supports
    * `Stream[Throwable, ByteBuffer]` streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[BlockingTask, BlockingZioStreams] =
    SttpBackendStub(new RIOMonadAsyncError[Blocking])
}
