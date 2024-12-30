package sttp.client4.httpclient.cats

import cats.effect.kernel.{Async, Resource, Sync}
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits.{toFlatMapOps, toFunctorOps}
import sttp.client4.httpclient.{HttpClientAsyncBackend, HttpClientBackend}
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.internal.httpclient._
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.internal.{emptyInputStream, NoStreams}
import sttp.client4.testing.WebSocketBackendStub
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.{wrappers, BackendOptions, WebSocketBackend}
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor
import sttp.client4.compression.GZipInputStreamDecompressor
import sttp.client4.compression.DeflateInputStreamDecompressor

class HttpClientCatsBackend[F[_]: Async] private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compressionHandlers: CompressionHandlers[Any, InputStream],
    dispatcher: Dispatcher[F]
) extends HttpClientAsyncBackend[F, Nothing, InputStream, InputStream](
      client,
      new CatsMonadAsyncError[F],
      closeClient,
      customizeRequest,
      compressionHandlers
    ) { self =>

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    Queue.unbounded[F, T].map(new CatsSimpleQueue(_, None, dispatcher))

  override protected def createSequencer: F[Sequencer[F]] = CatsSequencer.create

  override protected val bodyToHttpClient: BodyToHttpClient[F, Nothing, R] = new BodyToHttpClient[F, Nothing, R] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[F] = self.monad

    override def streamToPublisher(stream: Nothing): F[BodyPublisher] = stream // nothing is everything
    override def compressors: List[Compressor[R]] = compressionHandlers.compressors
  }

  override protected def bodyFromHttpClient: BodyFromHttpClient[F, Nothing, InputStream] =
    new InputStreamBodyFromHttpClient[F, Nothing] {
      override def inputStreamToStream(is: InputStream): F[(streams.BinaryStream, () => F[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))

      override val streams: NoStreams = NoStreams
      override implicit def monad: MonadError[F] = self.monad
      override def compileWebSocketPipe(
          ws: WebSocket[F],
          pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
      ): F[Unit] = pipe
    }

  override val streams: NoStreams = NoStreams

  override protected def createBodyHandler: HttpResponse.BodyHandler[InputStream] = BodyHandlers.ofInputStream()

  override protected def bodyHandlerBodyToBody(p: InputStream): InputStream = p

  override protected def emptyBody(): InputStream = emptyInputStream()
}

object HttpClientCatsBackend {
  val DefaultCompressionHandlers: CompressionHandlers[Any, InputStream] = CompressionHandlers(
    Compressor.default[Any],
    List(GZipInputStreamDecompressor, DeflateInputStreamDecompressor)
  )

  private def apply[F[_]: Async](
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      compressionHandlers: CompressionHandlers[Any, InputStream],
      dispatcher: Dispatcher[F]
  ): WebSocketBackend[F] =
    wrappers.FollowRedirectsBackend(
      new HttpClientCatsBackend(client, closeClient, customizeRequest, compressionHandlers, dispatcher)
    )

  def apply[F[_]: Async](
      dispatcher: Dispatcher[F],
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers
  ): F[WebSocketBackend[F]] =
    Async[F].executor.flatMap(executor =>
      Sync[F].delay(
        HttpClientCatsBackend(
          HttpClientBackend.defaultClient(options, Some(executor)),
          closeClient = false, // we don't want to close the underlying executor
          customizeRequest,
          compressionHandlers,
          dispatcher
        )
      )
    )

  def resource[F[_]: Async](
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers
  ): Resource[F, WebSocketBackend[F]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher =>
        Resource.make(apply(dispatcher, options, customizeRequest, compressionHandlers))(_.close())
      )

  def resourceUsingClient[F[_]: Async](
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers
  ): Resource[F, WebSocketBackend[F]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher =>
        Resource.make(
          Sync[F].delay(
            HttpClientCatsBackend(client, closeClient = true, customizeRequest, compressionHandlers, dispatcher)
          )
        )(_.close())
      )

  def usingClient[F[_]: Async](
      client: HttpClient,
      dispatcher: Dispatcher[F],
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers
  ): WebSocketBackend[F] =
    HttpClientCatsBackend(client, closeClient = false, customizeRequest, compressionHandlers, dispatcher)

  /** Create a stub backend for testing, which uses the [[F]] response wrapper.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: WebSocketBackendStub[F] = WebSocketBackendStub(new CatsMonadAsyncError[F])
}
