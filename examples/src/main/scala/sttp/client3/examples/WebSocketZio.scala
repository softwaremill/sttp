package sttp.client3.examples

import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.ws.WebSocket
import zio.{Console, _}

object WebSocketZio extends ZIOAppDefault {
  def useWebSocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    val receive = ws.receiveText().flatMap(t => Console.printLine(s"RECEIVED: $t"))
    send(1) *> send(2) *> receive *> receive
  }

  // create a description of a program, which requires SttpClient dependency in the environment
  def sendAndPrint(backend: WebSocketBackend[Task]): Task[Response[Unit]] =
    basicRequest.get(uri"wss://ws.postman-echo.com/raw").response(asWebSocketAlways(useWebSocket)).send(backend)

  override def run = {
    // provide an implementation for the SttpClient dependency
    HttpClientZioBackend.scoped().flatMap(sendAndPrint)
  }
}
