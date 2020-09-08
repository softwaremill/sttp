package sttp.client.examples

import sttp.client._
import sttp.client.asynchttpclient.zio._
import sttp.ws.WebSocket
import zio._
import zio.console.Console

object WebSocketZioEnv extends App {
  def useWebSocket(ws: WebSocket[RIO[Console, *]]): RIO[Console, Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    val receive = ws.receiveText().flatMap(t => console.putStrLn(s"RECEIVED: $t"))
    send(1) *> send(2) *> receive *> receive
  }

  // create a description of a program, which requires two dependencies in the environment:
  // the SttpClient, and the Console
  val sendAndPrint: RIO[Console with SttpClient, Response[Unit]] =
    sendR(basicRequest.get(uri"wss://echo.websocket.org").response(asWebSocketAlways(useWebSocket)))

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    // provide an implementation for the SttpClient dependency; other dependencies are
    // provided by Zio
    sendAndPrint
      .provideCustomLayer(AsyncHttpClientZioBackend.layer())
      .exitCode
  }
}
