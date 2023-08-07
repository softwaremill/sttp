package sttp.client4.examples

import sttp.client4._
import sttp.client4.akkahttp.AkkaHttpBackend
import sttp.ws.WebSocket

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object WebSocketSynchronous extends App {
  def useWebSocket(ws: WebSocket[Identity]): Unit = {
    def send(i: Int): Unit = ws.sendText(s"Hello $i!")
    def receive(): Unit = {
      val t = ws.receiveText()
      println(s"RECEIVED: $t")
    }
    send(1)
    send(2)
    receive()
    receive()
  }

  val backend = DefaultSyncBackend()

  try
    basicRequest
      .get(uri"wss://ws.postman-echo.com/raw")
      .response(asWebSocket[Identity, Unit](useWebSocket))
      .send(backend)
  finally backend.close()
}
