package sttp.client3.httpclient

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import sttp.client3.httpclient.HttpClientBackend.EncodingHandler
import sttp.client3.httpclient.HttpClientSyncBackend.SyncEncodingHandler
import sttp.client3.internal.NoStreams
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{
  FollowRedirectsBackend,
  Identity,
  Request,
  Response,
  ResponseAs,
  ResponseMetadata,
  SttpBackend,
  SttpBackendOptions,
  SttpClientException,
  WebSocketResponseAs
}
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}

class HttpClientSyncBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: SyncEncodingHandler
) extends HttpClientBackend[Identity, Nothing, Any, InputStream](client, closeClient, customEncodingHandler) {

  override val streams: NoStreams = NoStreams

  override def send[T, R >: PE](request: Request[T, R]): Identity[Response[T]] =
    adjustExceptions(request) {
      val jRequest = customizeRequest(convertRequest(request))
      val response = client.send(jRequest, BodyHandlers.ofInputStream())
      readResponse(response, Left(response.body()), request)
    }

  override def responseMonad: MonadError[Identity] = IdMonad

  private def adjustExceptions[T](request: Request[_, _])(t: => T): T =
    SttpClientException.adjustExceptions(responseMonad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  override protected val bodyToHttpClient: BodyToHttpClient[Identity, Nothing] =
    new BodyToHttpClient[Identity, Nothing] {
      override val streams: NoStreams = NoStreams
      override implicit val monad: MonadError[Identity] = IdMonad
      override def streamToPublisher(stream: Nothing): Identity[BodyPublisher] = stream // nothing is everything
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Identity, Nothing, InputStream] =
    new InputStreamBodyFromHttpClient[Identity, Nothing] {
      override def inputStreamToStream(is: InputStream): Identity[(streams.BinaryStream, () => Identity[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))
      override val streams: NoStreams = NoStreams
      override implicit def monad: MonadError[Identity] = IdMonad
      override def compileWebSocketPipe(
          ws: WebSocket[Identity],
          pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
      ): Identity[Unit] = pipe
    }

  override protected def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }
}

object HttpClientSyncBackend {
  type SyncEncodingHandler = EncodingHandler[InputStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: SyncEncodingHandler
  ): SttpBackend[Identity, Any] =
    new FollowRedirectsBackend(
      new HttpClientSyncBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: SyncEncodingHandler = PartialFunction.empty
  ): SttpBackend[Identity, Any] =
    HttpClientSyncBackend(
      HttpClientBackend.defaultClient(options),
      closeClient = true,
      customizeRequest,
      customEncodingHandler
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: SyncEncodingHandler = PartialFunction.empty
  ): SttpBackend[Identity, Any] =
    HttpClientSyncBackend(client, closeClient = false, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[Identity]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Identity, Any] = SttpBackendStub.synchronous
}
