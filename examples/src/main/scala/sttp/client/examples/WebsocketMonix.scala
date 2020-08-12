package sttp.client.examples

import monix.eval.Task
import sttp.client._
import sttp.client.asynchttpclient.monix.AsyncHttpClientMonixBackend
import sttp.ws.{WebSocket, WebSocketFrame}

object WebsocketMonix extends App {
  import monix.execution.Scheduler.Implicits.global

  def useWebsocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.send(WebSocketFrame.text(s"Hello $i!"))
    val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
    send(1) *> send(2) *> receive *> receive *> ws.close
  }

  val websocketTask: Task[Unit] = AsyncHttpClientMonixBackend.resource().use { backend =>
    basicRequest
      .response(asWebSocket(useWebsocket))
      .get(uri"wss://echo.websocket.org")
      .send(backend)
      .map(_ => ())
  }

  websocketTask.runSyncUnsafe()
}
