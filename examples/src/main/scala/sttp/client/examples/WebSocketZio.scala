package sttp.client.examples

import sttp.client._
import sttp.client.asynchttpclient.zio._
import sttp.ws.{WebSocket, WebSocketFrame}
import zio._
import zio.console.Console

object WebSocketZio extends App {
  def useWebSocket(ws: WebSocket[Task]): ZIO[Console, Throwable, Unit] = {
    def send(i: Int) = ws.send(WebSocketFrame.text(s"Hello $i!"))
    val receive = ws.receiveText().flatMap(t => console.putStrLn(s"RECEIVED: $t"))
    send(1) *> send(2) *> receive *> receive *> ws.close()
  }

  // create a description of a program, which requires two dependencies in the environment:
  // the SttpClient, and the Console
  val sendAndPrint: ZIO[Console with SttpClient, Throwable, Unit] = for {
    response <- SttpClient.send(basicRequest.get(uri"wss://echo.websocket.org").response(asWebSocketUnsafeAlways[Task]))
    _ <- useWebSocket(response.body)
  } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    // provide an implementation for the SttpClient dependency; other dependencies are
    // provided by Zio
    sendAndPrint
      .provideCustomLayer(AsyncHttpClientZioBackend.layer())
      .exitCode
  }
}
