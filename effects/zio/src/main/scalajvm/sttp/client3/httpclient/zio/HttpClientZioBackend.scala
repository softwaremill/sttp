package sttp.client3.httpclient.zio

import _root_.zio.interop.reactivestreams._
import org.reactivestreams.FlowAdapters
import sttp.capabilities.zio.ZioStreams
import sttp.client3.HttpClientBackend.EncodingHandler
import sttp.client3.impl.zio.{RIOMonadAsyncError, ZioSimpleQueue}
import sttp.client3.internal._
import sttp.client3.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, Sequencer}
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.testing.WebSocketStreamBackendStub
import sttp.client3.{
  FollowRedirectsBackend,
  HttpClientAsyncBackend,
  HttpClientBackend,
  BackendOptions,
  WebSocketStreamBackend
}
import sttp.monad.MonadError
import zio.Chunk.ByteArray
import zio._
import zio.stream.{ZPipeline, ZSink, ZStream}

import java.io.UnsupportedEncodingException
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.Flow.Publisher
import java.{util => ju}

class HttpClientZioBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler[ZioStreams.BinaryStream]
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
      customEncodingHandler
    )
    with WebSocketStreamBackend[Task, ZioStreams] {

  override val streams: ZioStreams = ZioStreams

  override protected def createBodyHandler: HttpResponse.BodyHandler[Publisher[util.List[ByteBuffer]]] =
    BodyHandlers.ofPublisher()

  override protected def emptyBody(): ZStream[Any, Throwable, Byte] = ZStream.empty

  override protected def bodyHandlerBodyToBody(p: Publisher[util.List[ByteBuffer]]): ZStream[Any, Throwable, Byte] =
    FlowAdapters.toPublisher(p).toZIOStream().mapConcatChunk { list =>
      val a = Chunk.fromJavaIterable(list).flatMap(_.safeRead()).toArray
      ByteArray(a, 0, a.length)
    }

  override protected val bodyToHttpClient: BodyToHttpClient[Task, ZioStreams] =
    new BodyToHttpClient[Task, ZioStreams] {
      override val streams: ZioStreams = ZioStreams
      override implicit def monad: MonadError[Task] = responseMonad
      override def streamToPublisher(stream: ZStream[Any, Throwable, Byte]): Task[BodyPublisher] = {
        import _root_.zio.interop.reactivestreams.{streamToPublisher => zioStreamToPublisher}
        val publisher = stream.mapChunks(byteChunk => Chunk(ByteBuffer.wrap(byteChunk.toArray))).toPublisher
        publisher.map { pub =>
          BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(pub))
        }
      }
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Task, ZioStreams, ZioStreams.BinaryStream] =
    new ZioBodyFromHttpClient

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    for {
      runtime <- ZIO.runtime[Any]
      queue <- Queue.unbounded[T]
    } yield new ZioSimpleQueue(queue, runtime)

  override protected def createSequencer: Task[Sequencer[Task]] = ZioSequencer.create

  override protected def standardEncoding: (ZStream[Any, Throwable, Byte], String) => ZStream[Any, Throwable, Byte] = {
    case (body, "gzip") => body.via(ZPipeline.gunzip())
    case (body, "deflate") =>
      ZStream.scoped(body.peel(ZSink.take[Byte](1))).flatMap { case (chunk, stream) =>
        val wrapped = chunk.headOption.exists(byte => (byte & 0x0f) == 0x08)
        (ZStream.fromChunk(chunk) ++ stream).via(ZPipeline.inflate(noWrap = !wrapped))
      }
    case (_, ce) => ZStream.fail(new UnsupportedEncodingException(s"Unsupported encoding: $ce"))
  }
}

object HttpClientZioBackend {

  type ZioEncodingHandler = EncodingHandler[ZioStreams.BinaryStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: ZioEncodingHandler
  ): WebSocketStreamBackend[Task, ZioStreams] =
    FollowRedirectsBackend(
      new HttpClientZioBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
             options: BackendOptions = BackendOptions.Default,
             customizeRequest: HttpRequest => HttpRequest = identity,
             customEncodingHandler: ZioEncodingHandler = PartialFunction.empty
  ): Task[WebSocketStreamBackend[Task, ZioStreams]] = {
    ZIO.executor.flatMap(executor =>
      ZIO.attempt(
        HttpClientZioBackend(
          HttpClientBackend.defaultClient(options, Some(executor.asJava)),
          closeClient = false, // we don't want to close ZIO's executor
          customizeRequest,
          customEncodingHandler
        )
      )
    )
  }

  def scoped(
              options: BackendOptions = BackendOptions.Default,
              customizeRequest: HttpRequest => HttpRequest = identity,
              customEncodingHandler: ZioEncodingHandler = PartialFunction.empty
  ): ZIO[Scope, Throwable, WebSocketStreamBackend[Task, ZioStreams]] =
    ZIO.acquireRelease(apply(options, customizeRequest, customEncodingHandler))(
      _.close().ignore
    )

  def scopedUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: ZioEncodingHandler = PartialFunction.empty
  ): ZIO[Scope, Throwable, WebSocketStreamBackend[Task, ZioStreams]] =
    ZIO.acquireRelease(
      ZIO.attempt(HttpClientZioBackend(client, closeClient = true, customizeRequest, customEncodingHandler))
    )(_.close().ignore)

  def layer(
             options: BackendOptions = BackendOptions.Default,
             customizeRequest: HttpRequest => HttpRequest = identity,
             customEncodingHandler: ZioEncodingHandler = PartialFunction.empty
  ): ZLayer[Any, Throwable, WebSocketStreamBackend[Task, ZioStreams]] = {
    ZLayer.scoped(
      (for {
        backend <- HttpClientZioBackend(
          options,
          customizeRequest,
          customEncodingHandler
        )
      } yield backend).tap(client => ZIO.addFinalizer(client.close().ignore))
    )
  }

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: ZioEncodingHandler = PartialFunction.empty
  ): WebSocketStreamBackend[Task, ZioStreams] =
    HttpClientZioBackend(
      client,
      closeClient = false,
      customizeRequest,
      customEncodingHandler
    )

  def layerUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: ZioEncodingHandler = PartialFunction.empty
  ): ZLayer[Any, Throwable, WebSocketStreamBackend[Task, ZioStreams]] = {
    ZLayer.scoped(
      ZIO
        .acquireRelease(
          ZIO.attempt(
            usingClient(
              client,
              customizeRequest,
              customEncodingHandler
            )
          )
        )(_.close().ignore)
    )
  }

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Stream[Throwable,
    * ByteBuffer]` streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, ZioStreams] = WebSocketStreamBackendStub(new RIOMonadAsyncError[Any])
}
