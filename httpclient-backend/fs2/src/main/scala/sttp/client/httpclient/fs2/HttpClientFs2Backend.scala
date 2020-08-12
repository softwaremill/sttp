package sttp.client.httpclient.fs2

import java.io.InputStream
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpRequest.BodyPublishers

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.concurrent.InspectableQueue
import fs2.{Pipe, Stream}
import fs2.interop.reactivestreams._
import org.reactivestreams.FlowAdapters
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.{FollowRedirectsBackend, Request, Response, SttpBackend, SttpBackendOptions}
import sttp.client.httpclient.{BodyFromHttpClient, BodyToHttpClient, HttpClientAsyncBackend, HttpClientBackend}
import sttp.client.impl.cats.implicits._
import sttp.client.impl.fs2.{Fs2SimpleQueue, Fs2WebSockets}
import sttp.client.internal.ws.SimpleQueue
import sttp.monad.MonadError
import sttp.client.testing.SttpBackendStub
import sttp.ws.{WebSocket, WebSocketFrame}

class HttpClientFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    client: HttpClient,
    blocker: Blocker,
    chunkSize: Int,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
) extends HttpClientAsyncBackend[F, Fs2Streams[F], Fs2Streams[F] with WebSockets](
      client,
      implicitly,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    super.send(request).guarantee(ContextShift[F].shift)

  override protected val bodyToHttpClient: BodyToHttpClient[F, Fs2Streams[F]] =
    new BodyToHttpClient[F, Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit def monad: MonadError[F] = responseMonad
      override def streamToPublisher(stream: Stream[F, Byte]): F[HttpRequest.BodyPublisher] =
        monad.eval(
          BodyPublishers.fromPublisher(
            FlowAdapters.toFlowPublisher(stream.chunks.map(_.toByteBuffer).toUnicastPublisher())
          )
        )
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[F, Fs2Streams[F]] =
    new BodyFromHttpClient[F, Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit def monad: MonadError[F] = responseMonad
      override def inputStreamToStream(is: InputStream): Stream[F, Byte] =
        fs2.io.readInputStream(is.pure[F], chunkSize, blocker)
      override def compileWebSocketPipe(
          ws: WebSocket[F],
          pipe: Pipe[F, WebSocketFrame.Data[_], WebSocketFrame]
      ): F[Unit] = Fs2WebSockets.handleThroughPipe(ws)(pipe)
    }

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    InspectableQueue.unbounded[F, T].map(new Fs2SimpleQueue(_))
}

object HttpClientFs2Backend {
  private val defaultChunkSize: Int = 4096

  private def apply[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      chunkSize: Int,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientFs2Backend(client, blocker, chunkSize, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      chunkSize: Int = defaultChunkSize,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Sync[F].delay(
      HttpClientFs2Backend(
        HttpClientBackend.defaultClient(options),
        blocker,
        chunkSize,
        closeClient = true,
        customizeRequest,
        customEncodingHandler
      )
    )

  def resource[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      chunkSize: Int = defaultChunkSize,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Resource.make(apply(blocker, chunkSize, options, customizeRequest, customEncodingHandler))(_.close())

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      chunkSize: Int = defaultChunkSize,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    HttpClientFs2Backend(client, blocker, chunkSize, closeClient = false, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[F]] response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent]: SttpBackendStub[F, Fs2Streams[F] with WebSockets] = SttpBackendStub(implicitly)
}
