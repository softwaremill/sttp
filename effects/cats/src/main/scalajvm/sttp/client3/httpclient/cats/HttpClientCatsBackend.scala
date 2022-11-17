package sttp.client3.httpclient.cats

import cats.effect.kernel.{Async, Resource, Sync}
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits.toFunctorOps
import sttp.capabilities.WebSockets
import sttp.client3.HttpClientBackend.EncodingHandler
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.internal.httpclient._
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.internal.{NoStreams, emptyInputStream}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{FollowRedirectsBackend, HttpClientAsyncBackend, HttpClientBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.Flow.Publisher
import java.util.zip.{GZIPInputStream, InflaterInputStream}

class HttpClientCatsBackend[F[_]: Async] private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler[InputStream],
    dispatcher: Dispatcher[F]
) extends HttpClientAsyncBackend[
      F,
      Nothing,
      WebSockets,
      InputStream
    ](
      client,
      new CatsMonadAsyncError[F],
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {
  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    Queue.unbounded[F, T].map(new CatsSimpleQueue(_, None, dispatcher))

  override protected def createSequencer: F[Sequencer[F]] = CatsSequencer.create

  override protected val bodyToHttpClient: BodyToHttpClient[F, Nothing] = new BodyToHttpClient[F, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[F] = responseMonad

    override def streamToPublisher(stream: Nothing): F[BodyPublisher] = stream // nothing is everything
  }

  override protected def bodyFromHttpClient: BodyFromHttpClient[F, Nothing, InputStream] =
    new InputStreamBodyFromHttpClient[F, Nothing] {
      override def inputStreamToStream(is: InputStream): F[(streams.BinaryStream, () => F[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))

      override val streams: NoStreams = NoStreams

      override implicit def monad: MonadError[F] = responseMonad

      override def compileWebSocketPipe(
          ws: WebSocket[F],
          pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
      ): F[Unit] = pipe
    }

  override protected def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  override protected def emptyBody(): InputStream = emptyInputStream()

  override protected def publisherToBody(p: Publisher[util.List[ByteBuffer]]): InputStream = {
    val subscriber = new InputStreamSubscriber
    p.subscribe(subscriber)
    subscriber.inputStream
  }

  override val streams: NoStreams = NoStreams
}

object HttpClientCatsBackend {

  private def apply[F[_]: Async](
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler[InputStream],
      dispatcher: Dispatcher[F]
  ): SttpBackend[F, WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientCatsBackend(client, closeClient, customizeRequest, customEncodingHandler, dispatcher)
    )

  def apply[F[_]: Async](
      dispatcher: Dispatcher[F],
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler[InputStream] = PartialFunction.empty
  ): F[SttpBackend[F, WebSockets]] =
    Sync[F].delay(
      HttpClientCatsBackend(
        HttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        customEncodingHandler,
        dispatcher
      )
    )

  def resource[F[_]: Async](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler[InputStream] = PartialFunction.empty
  ): Resource[F, SttpBackend[F, WebSockets]] =
    Dispatcher[F].flatMap(dispatcher =>
      Resource.make(apply(dispatcher, options, customizeRequest, customEncodingHandler))(_.close())
    )

  def usingClient[F[_]: Async](
      client: HttpClient,
      dispatcher: Dispatcher[F],
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler[InputStream] = PartialFunction.empty
  ): SttpBackend[F, WebSockets] =
    HttpClientCatsBackend(client, closeClient = false, customizeRequest, customEncodingHandler, dispatcher)

  /** Create a stub backend for testing, which uses the [[F]] response wrapper.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: SttpBackendStub[F, NoStreams] = SttpBackendStub(new CatsMonadAsyncError[F])
}
