package sttp.client.httpclient

import java.net.http.HttpClient
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.ArrayBlockingQueue

import com.github.ghik.silencer.silent
import sttp.client.monad.{IdMonad, MonadError}
import sttp.client.ws.WebSocketResponse
import sttp.client.{FollowRedirectsBackend, Identity, Request, Response, SttpBackend, SttpBackendOptions}
import sttp.model.Headers

class HttpClientSyncBackend(client: HttpClient) extends HttpClientBackend[Identity, Nothing](client) {
  override def send[T](request: Request[T, Nothing]): Identity[Response[T]] = {
    val jRequest = convertRequest(request)
    val response = client.send(jRequest, BodyHandlers.ofByteArray())
    readResponse(response, request.response)
  }

  override def responseMonad: MonadError[Identity] = IdMonad

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, Nothing],
      handler: WebSocketHandler[WS_RESULT]
  ): Identity[WebSocketResponse[WS_RESULT]] = {
//    val jRequest = convertRequest(request) //TODO we don't need it?

    val responseCell = new ArrayBlockingQueue[Either[Throwable, WebSocketResponse[WS_RESULT]]](1)
    @silent("discarded")
    def fillCellError(t: Throwable): Unit = responseCell.add(Left(t))
    @silent("discarded")
    def fillCell(wr: WebSocketResponse[WS_RESULT]): Unit = responseCell.add(Right(wr))
    val listener = new DelegatingWebSocketListener(
      handler.listener,
      webSocket => {
        val wsResponse = sttp.client.ws.WebSocketResponse(Headers.apply(Seq.empty), handler.wrIsWebSocket(webSocket))
        fillCell(wsResponse)
      },
      fillCellError,
      handler.wrIsWebSocket
    )
    HttpClient
      .newHttpClient()
      .newWebSocketBuilder()
      .buildAsync(request.uri.toJavaUri, listener)
    responseCell.take().fold(throw _, identity)
  }
}

object HttpClientSyncBackend {
  private def apply(client: HttpClient): SttpBackend[Identity, Nothing, WebSocketHandler] =
    new FollowRedirectsBackend[Identity, Nothing, WebSocketHandler](new HttpClientSyncBackend(client))

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): SttpBackend[Identity, Nothing, WebSocketHandler] =
    HttpClientSyncBackend(HttpBackend.defaultClient(options))

  def usingClient(client: HttpClient): SttpBackend[Identity, Nothing, WebSocketHandler] =
    HttpClientSyncBackend(client)
}
