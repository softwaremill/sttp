package sttp.client.httpclient.zio

import java.io.{File, UnsupportedEncodingException}
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer
import java.util

import org.reactivestreams.{FlowAdapters, Publisher}
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.BlockingZioStreams
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.{BodyFromHttpClient, BodyToHttpClient, HttpClientAsyncBackend, HttpClientBackend, RichByteBuffer}
import sttp.client.impl.zio.{RIOMonadAsyncError, ZioSimpleQueue, ZioWebSockets}
import sttp.client.internal.ws.SimpleQueue
import sttp.client.internal.{BodyFromResponseAs, SttpFile}
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client.{FollowRedirectsBackend, ResponseAs, ResponseMetadata, SttpBackend, SttpBackendOptions, WebSocketResponseAs}
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}
import zio._
import zio.blocking.Blocking
import _root_.zio.interop.reactivestreams.{streamToPublisher => zioStreamToPublisher, _}
import zio.Chunk.ByteArray
import zio.stream.{Stream, ZSink, ZStream, ZTransducer}

import scala.collection.JavaConverters._

class HttpClientZioBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler[BlockingZioStreams.BinaryStream],
    chunkSize: Int
) extends HttpClientAsyncBackend[
      BlockingTask,
      BlockingZioStreams,
      BlockingZioStreams with WebSockets,
      BlockingZioStreams.BinaryStream
    ](
      client,
      new RIOMonadAsyncError[Blocking],
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: BlockingZioStreams = BlockingZioStreams

  override protected def emptyBody(): ZStream[Blocking, Throwable, Byte] = ZStream.empty

  override protected def publisherToBody(p: Publisher[util.List[ByteBuffer]]): ZStream[Blocking, Throwable, Byte] =
    p.toStream().mapConcatChunk(list => ByteArray(list.asScala.toList.flatMap(_.safeRead()).toArray))

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

  override protected val bodyFromHttpClient
      : BodyFromHttpClient[BlockingTask, BlockingZioStreams, BlockingZioStreams.BinaryStream] =
    new BodyFromHttpClient[BlockingTask, BlockingZioStreams, BlockingZioStreams.BinaryStream] {
      override val streams: BlockingZioStreams = BlockingZioStreams
      override implicit def monad: MonadError[BlockingTask] = responseMonad

      override def compileWebSocketPipe(
          ws: WebSocket[BlockingTask],
          pipe: ZStream[Blocking, Throwable, WebSocketFrame.Data[_]] => ZStream[Blocking, Throwable, WebSocketFrame]
      ): BlockingTask[Unit] = ZioWebSockets.compilePipe(ws, pipe)

      override def apply[T](
          response: Either[ZStream[Blocking, Throwable, Byte], WebSocket[BlockingTask]],
          responseAs: ResponseAs[T, _],
          responseMetadata: ResponseMetadata
      ): BlockingTask[T] = {
        new BodyFromResponseAs[BlockingTask, BlockingZioStreams.BinaryStream, WebSocket[
          BlockingTask
        ], BlockingZioStreams.BinaryStream] {
          override protected def withReplayableBody(
              response: ZStream[Blocking, Throwable, Byte],
              replayableBody: Either[Array[Byte], SttpFile]
          ): Task[ZStream[Blocking, Throwable, Byte]] = {
            replayableBody match {
              case Left(byteArray) => ZIO.succeed(Stream.fromIterable(byteArray))
              case Right(file)     => ZIO.succeed(Stream.fromFile(file.toPath))
            }
          }

          override protected def regularIgnore(response: ZStream[Blocking, Throwable, Byte]): BlockingTask[Unit] =
            response.run(ZSink.drain)

          override protected def regularAsByteArray(
              response: ZStream[Blocking, Throwable, Byte]
          ): BlockingTask[Array[Byte]] = response.run(ZSink.collectAll[Byte]).map(_.toArray)

          override protected def regularAsFile(
              response: ZStream[Blocking, Throwable, Byte],
              file: SttpFile
          ): BlockingTask[SttpFile] = response.run(ZSink.fromFile(file.toPath)).as(file)

          override protected def regularAsStream(
              response: ZStream[Blocking, Throwable, Byte]
          ): Task[(ZStream[Blocking, Throwable, Byte], () => BlockingTask[Unit])] =
            Task.effect(response -> { () => response.runDrain })

          override protected def handleWS[T](
              responseAs: WebSocketResponseAs[T, _],
              meta: ResponseMetadata,
              ws: WebSocket[BlockingTask]
          ): BlockingTask[T] = bodyFromWs(responseAs, ws)

          override protected def cleanupWhenNotAWebSocket(
              response: ZStream[Blocking, Throwable, Byte],
              e: NotAWebSocketException
          ): BlockingTask[Unit] = response.run(ZSink.drain)

          override protected def cleanupWhenGotWebSocket(
              response: WebSocket[BlockingTask],
              e: GotAWebSocketException
          ): BlockingTask[Unit] = response.close()
        }.apply(responseAs, responseMetadata, response)
      }
    }

  override protected def createSimpleQueue[T]: BlockingTask[SimpleQueue[BlockingTask, T]] =
    for {
      runtime <- ZIO.runtime[Any]
      queue <- Queue.unbounded[T]
    } yield new ZioSimpleQueue(queue, runtime)

  override protected def standardEncoding
      : (ZStream[Blocking, Throwable, Byte], String) => ZStream[Blocking, Throwable, Byte] = {
    case (body, "gzip")    => body.transduce(ZTransducer.gunzip())
    case (body, "deflate") => body.transduce(ZTransducer.inflate())
    case (_, ce)           => ZStream.fail(new UnsupportedEncodingException(s"Unsupported encoding: $ce"))
  }
}

object HttpClientZioBackend {

  private val defaultChunkSize = 65536
  type ZioEncodingHandler = EncodingHandler[BlockingZioStreams.BinaryStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: ZioEncodingHandler,
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
      customEncodingHandler: ZioEncodingHandler = PartialFunction.empty,
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
      customEncodingHandler: ZioEncodingHandler = PartialFunction.empty,
      chunkSize: Int = defaultChunkSize
  ): ZManaged[Blocking, Throwable, SttpBackend[BlockingTask, BlockingZioStreams with WebSockets]] =
    ZManaged.make(apply(options, customizeRequest, customEncodingHandler, chunkSize))(
      _.close().ignore
    )

  def layer(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: ZioEncodingHandler = PartialFunction.empty,
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
      customEncodingHandler: ZioEncodingHandler = PartialFunction.empty,
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
      customEncodingHandler: ZioEncodingHandler = PartialFunction.empty,
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

  val stubLayer: ZLayer[Any, Nothing, SttpClientStubbing with SttpClient] = SttpClientStubbing.layer
}
