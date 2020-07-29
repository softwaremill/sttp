package sttp.client.httpclient

import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.ArrayBlockingQueue

import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.internal.NoStreams
import sttp.client.monad.{IdMonad, MonadError}
import sttp.client.testing.SttpBackendStub
import sttp.client.{
  Effect,
  FollowRedirectsBackend,
  Identity,
  Request,
  Response,
  Streams,
  SttpBackend,
  SttpBackendOptions,
  SttpClientException
}
import sttp.model.Headers

class HttpClientSyncBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
) extends HttpClientBackend[Identity, Nothing, Any](client, closeClient, customEncodingHandler) {

  override val streams: NoStreams = NoStreams

  override def send[T, R >: PE](request: Request[T, R]): Identity[Response[T]] =
    adjustExceptions {
      val jRequest = customizeRequest(convertRequest(request))
      val response = client.send(jRequest, BodyHandlers.ofInputStream())
      readResponse(response, request.response)
    }

  override def responseMonad: MonadError[Identity] = IdMonad

  override def openWebsocket[T, WS_RESULT, R >: PE](
      request: Request[T, R],
      handler: WebSocketHandler[WS_RESULT]
  ): Identity[WebSocketResponse[WS_RESULT]] =
    adjustExceptions {
      val responseCell = new ArrayBlockingQueue[Either[Throwable, WebSocketResponse[WS_RESULT]]](1)
      def fillCellError(t: Throwable): Unit = responseCell.add(Left(t))
      def fillCell(wr: WebSocketResponse[WS_RESULT]): Unit = responseCell.add(Right(wr))
      val listener = new DelegatingWebSocketListener(
        handler.listener,
        webSocket => {
          val wsResponse = sttp.client.ws.WebSocketResponse(Headers.apply(Seq.empty), handler.createResult(webSocket))
          fillCell(wsResponse)
        },
        fillCellError
      )
      client
        .newWebSocketBuilder()
        .buildAsync(request.uri.toJavaUri, listener)
      responseCell.take().fold(throw _, identity)
    }

  private def adjustExceptions[T](t: => T): T =
    SttpClientException.adjustSynchronousExceptions(t)(SttpClientException.defaultExceptionToSttpClientException)
}

object HttpClientSyncBackend {
  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler
  ): SttpBackend[Identity, Any, WebSocketHandler] =
    new FollowRedirectsBackend(
      new HttpClientSyncBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Identity, Any, WebSocketHandler] =
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
  ): SttpBackend[Identity, Any, WebSocketHandler] =
    HttpClientSyncBackend(client, closeClient = false, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[Identity]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Identity, Any, WebSocketHandler] = SttpBackendStub.synchronous
}
