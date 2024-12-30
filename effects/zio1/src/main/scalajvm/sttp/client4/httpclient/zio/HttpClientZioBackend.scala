package sttp.client4.httpclient.zio

import _root_.zio.interop.reactivestreams._
import org.reactivestreams.FlowAdapters
import sttp.capabilities.zio.ZioStreams
import sttp.client4.httpclient.{HttpClientAsyncBackend, HttpClientBackend}
import sttp.client4.impl.zio.{RIOMonadAsyncError, ZioSimpleQueue}
import sttp.client4.internal._
import sttp.client4.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, Sequencer}
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4.{wrappers, BackendOptions, GenericRequest, Response, WebSocketStreamBackend}
import sttp.monad.MonadError
import zio.Chunk.ByteArray
import zio._
import zio.stream.ZStream

import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.Flow.Publisher
import java.{util => ju}
import scala.collection.JavaConverters._
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor

class HttpClientZioBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream]
) extends HttpClientAsyncBackend[
      Task,
      ZioStreams,
      Publisher[ju.List[ByteBuffer]],
      ZioStreams.BinaryStream
    ](
      client,
      new RIOMonadAsyncError[Any],
      closeClient,
      customizeRequest,
      compressionHandlers
    )
    with WebSocketStreamBackend[Task, ZioStreams] { self =>

  override val streams: ZioStreams = ZioStreams

  override protected def createBodyHandler: HttpResponse.BodyHandler[Publisher[util.List[ByteBuffer]]] =
    BodyHandlers.ofPublisher()

  override protected def emptyBody(): ZStream[Any, Throwable, Byte] = ZStream.empty

  override protected def bodyHandlerBodyToBody(p: Publisher[util.List[ByteBuffer]]): ZStream[Any, Throwable, Byte] =
    FlowAdapters
      .toPublisher(p)
      .toStream()
      .mapConcatChunk(list => ByteArray(list.asScala.toList.flatMap(_.safeRead()).toArray))

  override protected val bodyToHttpClient: BodyToHttpClient[Task, ZioStreams, R] =
    new BodyToHttpClient[Task, ZioStreams, R] {
      override val streams: ZioStreams = ZioStreams
      override implicit def monad: MonadError[Task] = self.monad
      override def streamToPublisher(stream: ZStream[Any, Throwable, Byte]): Task[BodyPublisher] = {
        import _root_.zio.interop.reactivestreams.{streamToPublisher => zioStreamToPublisher}
        val publisher = stream.mapChunks(byteChunk => Chunk(ByteBuffer.wrap(byteChunk.toArray))).toPublisher
        publisher.map { pub =>
          BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(pub))
        }
      }
      override def compressors: List[Compressor[R]] = compressionHandlers.compressors
    }

  override def send[T](request: GenericRequest[T, R]): Task[Response[T]] =
    super.send(request).resurrect

  override protected val bodyFromHttpClient: BodyFromHttpClient[Task, ZioStreams, ZioStreams.BinaryStream] =
    new ZioBodyFromHttpClient

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    for {
      runtime <- ZIO.runtime[Any]
      queue <- Queue.unbounded[T]
    } yield new ZioSimpleQueue(queue, runtime)

  override protected def createSequencer: Task[Sequencer[Task]] = ZioSequencer.create
}

object HttpClientZioBackend {
  val DefaultCompressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] =
    CompressionHandlers(
      Compressor.default,
      List(GZipZioDecompressor, DeflateZioDecompressor)
    )

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream]
  ): WebSocketStreamBackend[Task, ZioStreams] =
    wrappers.FollowRedirectsBackend(
      new HttpClientZioBackend(client, closeClient, customizeRequest, compressionHandlers)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): Task[WebSocketStreamBackend[Task, ZioStreams]] =
    UIO.executor.flatMap(executor =>
      Task.effect(
        HttpClientZioBackend(
          HttpClientBackend.defaultClient(options, Some(executor.asJava)),
          closeClient = false, // we don't want to close ZIO's executor
          customizeRequest,
          compressionHandlers
        )
      )
    )

  def managed(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZManaged[Any, Throwable, WebSocketStreamBackend[Task, ZioStreams]] =
    ZManaged.make(apply(options, customizeRequest, compressionHandlers))(
      _.close().ignore
    )

  def layer(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZLayer[Any, Throwable, SttpClient] =
    ZLayer.fromManaged(
      (for {
        backend <- HttpClientZioBackend(
          options,
          customizeRequest,
          compressionHandlers
        )
      } yield backend).toManaged(_.close().ignore)
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): WebSocketStreamBackend[Task, ZioStreams] =
    HttpClientZioBackend(
      client,
      closeClient = false,
      customizeRequest,
      compressionHandlers
    )

  def layerUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZLayer[Any, Throwable, SttpClient] =
    ZLayer.fromManaged(
      ZManaged
        .makeEffect(
          usingClient(
            client,
            customizeRequest,
            compressionHandlers
          )
        )(_.close().ignore)
    )

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Stream[Throwable,
    * ByteBuffer]` streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, ZioStreams] = WebSocketStreamBackendStub(new RIOMonadAsyncError[Any])

  val stubLayer: ZLayer[Any, Nothing, SttpClientStubbing with SttpClient] = SttpClientStubbing.layer
}
