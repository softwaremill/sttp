package sttp.client.httpclient

import java.io.InputStream
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}

import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.internal.NoStreams
import sttp.client.monad.IdMonad
import sttp.client.testing.SttpBackendStub
import sttp.client.{
  FollowRedirectsBackend,
  Identity,
  Request,
  Response,
  SttpBackend,
  SttpBackendOptions,
  SttpClientException
}
import sttp.monad.MonadError
import sttp.ws.WebSocket

class HttpClientSyncBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
) extends HttpClientBackend[Identity, Nothing, Any](client, closeClient, customEncodingHandler) {

  override val streams: NoStreams = NoStreams

  override def send[T, R >: PE](request: Request[T, R]): Identity[Response[T]] =
    adjustExceptions(request) {
      val jRequest = customizeRequest(convertRequest(request))
      val response = client.send(jRequest, BodyHandlers.ofInputStream())
      readResponse(response, Left(response.body()), request.response)
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

  override protected val bodyFromHttpClient: BodyFromHttpClient[Identity, Nothing] =
    new BodyFromHttpClient[Identity, Nothing] {
      override val streams: NoStreams = NoStreams
      override implicit val monad: MonadError[Identity] = IdMonad
      override def inputStreamToStream(is: InputStream): Nothing =
        throw new IllegalStateException("Streaming is not supported")
      override def compileWebSocketPipe(ws: WebSocket[Identity], pipe: Nothing): Identity[Unit] =
        pipe // nothing is everything
    }
}

object HttpClientSyncBackend {
  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler
  ): SttpBackend[Identity, Any] =
    new FollowRedirectsBackend(
      new HttpClientSyncBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
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
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Identity, Any] =
    HttpClientSyncBackend(client, closeClient = false, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[Identity]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Identity, Any] = SttpBackendStub.synchronous
}
