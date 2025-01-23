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
import sttp.client4.compression.Compressor
import sttp.client4.impl.zio.{DeflateZioCompressor, DeflateZioDecompressor, GZipZioCompressor, GZipZioDecompressor}
import sttp.client4.compression.CompressionHandlers
import sttp.monad.Canceler
import sttp.client4.internal.SttpToJavaConverters.toJavaBiConsumer

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
    FlowAdapters.toPublisher(p).toZIOStream().mapConcatChunk { list =>
      val a = Chunk.fromJavaIterable(list).flatMap(_.safeRead()).toArray
      ByteArray(a, 0, a.length)
    }

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

  override def sendRegular[T](request: GenericRequest[T, R]): Task[Response[T]] = {
    monad
      .flatMap(convertRequest(request)) { convertedRequest =>
        val jRequest = customizeRequest(convertedRequest)

        val hcResponse = ZIO.acquireReleaseExit {
          monad.async[HttpResponse[Publisher[ju.List[ByteBuffer]]]] { cb =>
            def success(r: HttpResponse[Publisher[ju.List[ByteBuffer]]]): Unit = cb(Right(r))
            def error(t: Throwable): Unit = cb(Left(t))
            val cf = client.sendAsync(jRequest, createBodyHandler)

            cf.whenComplete(toJavaBiConsumer { (t: HttpResponse[Publisher[ju.List[ByteBuffer]]], u: Throwable) =>
              if (t != null) {
                success(t)
              }
              if (u != null) {
                error(u)
              }
            })

            Canceler(() => cf.cancel(true))
          }
        } { case (r, exit) =>
          // we only ensure that the publisher is consumed on non-successful results (failure or interruption); in case
          // of success, the body is either fully consumed as specified in the response description, or when an
          // `...Unsafe` response description is used, it's up to the user to consume it
          if (!exit.isSuccess) {
            ZIO.attempt {
              // if there already was a subscriber, we'll receive an onError callback immediately
              r.body()
                .subscribe(new java.util.concurrent.Flow.Subscriber[ju.List[ByteBuffer]] {
                  override def onSubscribe(s: java.util.concurrent.Flow.Subscription): Unit = s.cancel()
                  override def onNext(t: ju.List[ByteBuffer]): Unit = ()
                  override def onError(t: Throwable): Unit = ()
                  override def onComplete(): Unit = ()
                })
            }.orDie
          } else ZIO.unit
        }

        ZIO.scoped {
          hcResponse.flatMap { t =>
            // sometimes body returned by HttpClient can be null, we handle this by returning empty body to prevent NPE
            val body = Option(t.body())
              .map(bodyHandlerBodyToBody)
              .getOrElse(emptyBody())

            val limitedBody = request.options.maxResponseBodyLength.fold(body)(bodyToLimitedBody(body, _))

            readResponse(t, Left(limitedBody), request)
          }
        }
      }
      .resurrect
  }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Task, ZioStreams, ZioStreams.BinaryStream] =
    new ZioBodyFromHttpClient

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    for {
      runtime <- ZIO.runtime[Any]
      queue <- Queue.unbounded[T]
    } yield new ZioSimpleQueue(queue, runtime)

  override protected def createSequencer: Task[Sequencer[Task]] = ZioSequencer.create

  override protected def bodyToLimitedBody(b: ZioStreams.BinaryStream, limit: Long): ZioStreams.BinaryStream =
    ZioStreams.limitBytes(b, limit)
}

object HttpClientZioBackend {
  val DefaultCompressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] =
    CompressionHandlers(
      List(GZipZioCompressor, DeflateZioCompressor),
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
    ZIO.executor.flatMap(executor =>
      ZIO.attempt(
        HttpClientZioBackend(
          HttpClientBackend.defaultClient(options, Some(executor.asJava)),
          closeClient = false, // we don't want to close ZIO's executor
          customizeRequest,
          compressionHandlers
        )
      )
    )

  def scoped(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZIO[Scope, Throwable, WebSocketStreamBackend[Task, ZioStreams]] =
    ZIO.acquireRelease(apply(options, customizeRequest, compressionHandlers))(
      _.close().ignore
    )

  def scopedUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZIO[Scope, Throwable, WebSocketStreamBackend[Task, ZioStreams]] =
    ZIO.acquireRelease(
      ZIO.attempt(HttpClientZioBackend(client, closeClient = true, customizeRequest, compressionHandlers))
    )(_.close().ignore)

  def layer(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZLayer[Any, Throwable, SttpClient] =
    ZLayer.scoped(
      (for {
        backend <- HttpClientZioBackend(
          options,
          customizeRequest,
          compressionHandlers
        )
      } yield backend).tap(client => ZIO.addFinalizer(client.close().ignore))
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
    ZLayer.scoped(
      ZIO
        .acquireRelease(
          ZIO.attempt(
            usingClient(
              client,
              customizeRequest,
              compressionHandlers
            )
          )
        )(_.close().ignore)
    )

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Stream[Throwable,
    * ByteBuffer]` streaming.
    *
    * See [[WebSocketStreamBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, ZioStreams] = WebSocketStreamBackendStub(new RIOMonadAsyncError[Any])
}
