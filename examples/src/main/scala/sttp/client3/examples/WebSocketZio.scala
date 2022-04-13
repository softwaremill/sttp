package sttp.client3.examples

import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.ws.WebSocket
import zio._
import zio.Console

object WebSocketZio extends ZIOAppDefault {
  def useWebSocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    val receive = ws.receiveText().flatMap(t => Console.printLine(s"RECEIVED: $t")).orDie
    send(1) *> send(2) *> receive *> receive
  }

  // create a description of a program, which requires one dependency in the environment:
  // the SttpClient
  val sendAndPrint: RIO[SttpClient, Response[Unit]] =
    send(basicRequest.get(uri"wss://echo.websocket.org").response(asWebSocketAlways(useWebSocket)))

  override def run = {
    // provide an implementation for the SttpClient dependency; other dependencies are
    // provided by Zio
    sendAndPrint
      .provide(AsyncHttpClientZioBackend.layer())
      .exitCode
  }
}
