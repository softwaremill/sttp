package sttp.client.httpclient.fs2

import java.io.InputStream
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpRequest.BodyPublishers

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.Stream
import fs2.interop.reactivestreams._
import org.reactivestreams.FlowAdapters
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.client.httpclient.{HttpClientAsyncBackend, HttpClientBackend, WebSocketHandler}
import sttp.client.impl.cats.implicits._
import sttp.client.testing.SttpBackendStub

import scala.util.{Success, Try}
import sttp.client.ws.WebSocketResponse

class HttpClientFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    client: HttpClient,
    blocker: Blocker,
    chunkSize: Int,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
) extends HttpClientAsyncBackend[F, Stream[F, Byte]](
      client,
      implicitly,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override def openWebsocket[T, WS_RESULT](
      request: sttp.client.Request[T, Stream[F, Byte]],
      handler: WebSocketHandler[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] =
    super.openWebsocket(request, handler).guarantee(ContextShift[F].shift)

  override def streamToRequestBody(stream: Stream[F, Byte]): F[HttpRequest.BodyPublisher] =
    monad.eval(
      BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(stream.chunks.map(_.toByteBuffer).toUnicastPublisher()))
    )

  override def responseBodyToStream(responseBody: InputStream): Try[Stream[F, Byte]] =
    Success(fs2.io.readInputStream(responseBody.pure[F], chunkSize, blocker))
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
  ): SttpBackend[F, Stream[F, Byte], WebSocketHandler] =
    new FollowRedirectsBackend(
      new HttpClientFs2Backend(client, blocker, chunkSize, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      chunkSize: Int = defaultChunkSize,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): F[SttpBackend[F, Stream[F, Byte], WebSocketHandler]] =
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
  ): Resource[F, SttpBackend[F, Stream[F, Byte], WebSocketHandler]] =
    Resource.make(apply(blocker, chunkSize, options, customizeRequest, customEncodingHandler))(_.close())

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      chunkSize: Int = defaultChunkSize,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[F, Stream[F, Byte], WebSocketHandler] =
    HttpClientFs2Backend(client, blocker, chunkSize, closeClient = false, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[F]] response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent]: SttpBackendStub[F, Stream[F, Byte], WebSocketHandler] = SttpBackendStub(implicitly)
}
