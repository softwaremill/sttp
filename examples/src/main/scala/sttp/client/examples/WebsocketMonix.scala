package sttp.client.examples

import monix.eval.Task
import sttp.client._
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame
import sttp.client.asynchttpclient.monix.AsyncHttpClientMonixBackend

object WebsocketMonix extends App {
  import monix.execution.Scheduler.Implicits.global

  def useWebsocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.send(WebSocketFrame.text(s"Hello $i!"))
    val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
    send(1) *> send(2) *> receive *> receive *> ws.close
  }

  val websocketTask: Task[Unit] = AsyncHttpClientMonixBackend.resource().use { implicit backend =>
    basicRequest
      .response(asWebSocket(useWebsocket))
      .get(uri"wss://echo.websocket.org")
      .send()
      .map(_ => ())
  }

  websocketTask.runSyncUnsafe()
}
