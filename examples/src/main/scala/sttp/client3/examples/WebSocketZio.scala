package sttp.client3.examples

import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.client3.impl.zio.{SttpClientWebSockets, sendRWebSockets}
import sttp.ws.WebSocket
import zio.{Console, _}

object WebSocketZio extends ZIOAppDefault {
  def useWebSocket(ws: WebSocket[Task[*]]): Task[Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    val receive = ws.receiveText().flatMap(t => Console.printLine(s"RECEIVED: $t"))
    send(1) *> send(2) *> receive *> receive
  }

  // create a description of a program, which requires SttpClient dependency in the environment
  val sendAndPrint: RIO[SttpClientWebSockets, Response[Unit]] =
    sendRWebSockets(basicRequest.get(uri"wss://echo.websocket.org").response(asWebSocketAlways(useWebSocket)))

  override def run = {
    // provide an implementation for the SttpClient dependency
    sendAndPrint
      .provide(AsyncHttpClientZioBackend.layer())
  }
}
