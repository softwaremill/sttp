package sttp.client3.examples

import sttp.client3._
import sttp.client3.pekkohttp.PekkoHttpBackend
import sttp.ws.WebSocket

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object WebSocketPekko extends App {
  def useWebSocket(ws: WebSocket[Future]): Future[Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    def receive() = ws.receiveText().map(t => println(s"RECEIVED: $t"))
    for {
      _ <- send(1)
      _ <- send(2)
      _ <- receive()
      _ <- receive()
    } yield ()
  }

  val backend = PekkoHttpBackend()

  basicRequest
    .get(uri"wss://ws.postman-echo.com/raw")
    .response(asWebSocket(useWebSocket))
    .send(backend)
    .onComplete(_ => backend.close())
}
