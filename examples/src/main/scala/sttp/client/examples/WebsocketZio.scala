package sttp.client.examples

object WebsocketZio extends zio.App {
  import sttp.client._
  import sttp.client.asynchttpclient.zio._
  import sttp.client.ws.{WebSocket, WebSocketResponse}
  import sttp.model.ws.WebSocketFrame
  import zio._

  def useWebsocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.send(WebSocketFrame.text(s"Hello $i!"))
    val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
    send(1) *> send(2) *> receive *> receive *> ws.close
  }

  val websocketTask: Task[Unit] = AsyncHttpClientZioBackend().flatMap { implicit backend =>
    val response: Task[WebSocketResponse[WebSocket[Task]]] = basicRequest
      .get(uri"wss://echo.websocket.org")
      .openWebsocketF(ZioWebSocketHandler())

    response
      .flatMap(r => useWebsocket(r.result))
      .ensuring(backend.close().catchAll(_ => ZIO.unit))
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    websocketTask.fold(_ => 1, _ => 0)
  }
}
