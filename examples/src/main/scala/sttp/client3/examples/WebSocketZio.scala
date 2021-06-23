package sttp.client3.examples

import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.ws.WebSocketFrame
import zio._
import zio.stream.Stream
import sttp.capabilities.zio.ZioStreams
import ZioStreams.Pipe

object WebSocketZio extends App {
  def webSocketFramePipe: Pipe[WebSocketFrame.Data[_], WebSocketFrame] = { input =>
    Stream(WebSocketFrame.text("1")) ++ input.flatMap {
      case WebSocketFrame.Text("10", _, _) =>
        println("Received 10 messages, sending close frame")
        Stream(WebSocketFrame.close)
      case WebSocketFrame.Text(n, _, _) =>
        val next = (n.toInt + 1).toString
        println(s"Received $n messages, replying with $next")
        Stream(WebSocketFrame.text(next))
      case _ => Stream.empty // ignoring
    }
  }

  // create a description of a program, which requires SttpClient dependency in the environment
  val sendAndPrint: RIO[SttpClient, Response[Either[String, Unit]]] =
    sendR(
      basicRequest
        .get(uri"wss://echo.websocket.org")
        .response(asWebSocketStream(ZioStreams)(webSocketFramePipe))
    )

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    // provide an implementation for the SttpClient dependency; other dependencies (if required) are
    // provided by Zio
    sendAndPrint
      .provideCustomLayer(AsyncHttpClientZioBackend.layer())
      .exitCode
  }
}
