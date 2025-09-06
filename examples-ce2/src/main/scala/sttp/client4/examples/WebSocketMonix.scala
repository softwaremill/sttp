// {cat=WebSocket; effects=Monix; backend=HttpClient}: Connect to & interact with a WebSocket

//> using scala 2.13
//> using dep com.softwaremill.sttp.client4::monix:4.0.10

package sttp.client4.examples

import monix.eval.Task
import sttp.client4._
import sttp.client4.ws.async._
import sttp.client4.httpclient.monix.HttpClientMonixBackend
import sttp.ws.WebSocket

object WebSocketMonix extends App {
  import monix.execution.Scheduler.Implicits.global

  def useWebSocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
    send(1) *> send(2) *> receive *> receive
  }

  HttpClientMonixBackend
    .resource()
    .use { backend =>
      basicRequest
        .get(uri"wss://ws.postman-echo.com/raw")
        .response(asWebSocket(useWebSocket))
        .send(backend)
        .void
    }
    .runSyncUnsafe()
}
