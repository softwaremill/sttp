package sttp.client.examples

import cats.effect.{ContextShift, IO}
import fs2._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.ws.WebSocketFrame

object WebSocketStreamFs2 extends App {
  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  def webSocketFramePipe: Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] = { input =>
    Stream.emit(WebSocketFrame.text("1")) ++ input.flatMap {
      case WebSocketFrame.Text("10", _, _) =>
        println("Received 10 messages, sending close frame")
        Stream.emit(WebSocketFrame.close)
      case WebSocketFrame.Text(n, _, _) =>
        println(s"Received $n messages, replying with $n+1")
        Stream.emit(WebSocketFrame.text((n.toInt + 1).toString))
      case _ => Stream.empty // ignoring
    }
  }

  AsyncHttpClientFs2Backend
    .resource[IO]()
    .use { backend =>
      basicRequest
        .response(asWebSocketStream(Fs2Streams[IO])(webSocketFramePipe))
        .get(uri"wss://echo.websocket.org")
        .send(backend)
        .void
    }
    .unsafeRunSync()
}
