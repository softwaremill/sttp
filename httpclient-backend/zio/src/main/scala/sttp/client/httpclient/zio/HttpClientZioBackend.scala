package sttp.client.httpclient.zio

import java.io.UnsupportedEncodingException
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer
import java.util

import _root_.zio.interop.reactivestreams._
import org.reactivestreams.{FlowAdapters, Publisher}
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.BlockingZioStreams
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.{BodyFromHttpClient, BodyToHttpClient, HttpClientAsyncBackend, HttpClientBackend}
import sttp.client.internal._
import sttp.client.impl.zio.{RIOMonadAsyncError, ZioSimpleQueue}
import sttp.client.internal.ws.SimpleQueue
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadError
import zio.Chunk.ByteArray
import zio._
import zio.blocking.Blocking
import zio.stream.{ZStream, ZTransducer}

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
        import _root_.zio.interop.reactivestreams.{streamToPublisher => zioStreamToPublisher}
        val publisher = stream.mapChunks(byteChunk => Chunk(ByteBuffer.wrap(byteChunk.toArray))).toPublisher
        publisher.map { pub =>
          BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(pub))
        }
      }
    }

  override protected val bodyFromHttpClient
      : BodyFromHttpClient[BlockingTask, BlockingZioStreams, BlockingZioStreams.BinaryStream] =
    new ZioBodyFromHttpClient

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
