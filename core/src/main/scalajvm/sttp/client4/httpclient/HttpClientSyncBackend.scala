package sttp.client4.httpclient

import sttp.client4.httpclient.HttpClientBackend.EncodingHandler
import sttp.client4.httpclient.HttpClientSyncBackend.SyncEncodingHandler
import sttp.client4.internal.NoStreams
import sttp.client4.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, InputStreamBodyFromHttpClient}
import sttp.client4.monad.IdMonad
import sttp.client4.testing.SyncBackendStub
import sttp.client4.{wrappers, BackendOptions, GenericRequest, Identity, Response, SttpClientException, SyncBackend}
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.util.zip.{GZIPInputStream, InflaterInputStream}

class HttpClientSyncBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: SyncEncodingHandler
) extends HttpClientBackend[Identity, Nothing, Any, InputStream](
      client,
      closeClient,
      customEncodingHandler
    )
    with SyncBackend {

  override val streams: NoStreams = NoStreams

  override def send[T](request: GenericRequest[T, R]): Response[T] =
    adjustExceptions(request) {
      val jRequest = customizeRequest(convertRequest(request))
      val response = client.send(jRequest, BodyHandlers.ofInputStream())
      readResponse(response, Left(response.body()), request)
    }

  override def monad: MonadError[Identity] = IdMonad

  private def adjustExceptions[T](request: GenericRequest[_, R])(t: => T): T =
    SttpClientException.adjustExceptions(monad)(t)(
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
  ): SyncBackend =
    wrappers.FollowRedirectsBackend(
      new HttpClientSyncBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: SyncEncodingHandler = PartialFunction.empty
  ): SyncBackend =
    HttpClientSyncBackend(
      HttpClientBackend.defaultClient(options, None),
      closeClient = true,
      customizeRequest,
      customEncodingHandler
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: SyncEncodingHandler = PartialFunction.empty
  ): SyncBackend =
    HttpClientSyncBackend(
      client,
      closeClient = false,
      customizeRequest,
      customEncodingHandler
    )

  /** Create a stub backend for testing. See [[SyncBackendStub]] for details on how to configure stub responses. */
  def stub: SyncBackendStub = SyncBackendStub
}
