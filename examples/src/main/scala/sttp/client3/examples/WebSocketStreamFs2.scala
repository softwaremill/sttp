package sttp.client3.examples

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.ws.WebSocketFrame

object WebSocketStreamFs2 extends App {
  implicit val runtime: IORuntime = cats.effect.unsafe.implicits.global

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

  HttpClientFs2Backend
    .resource[IO]()
    .use { backend =>
      basicRequest
        .response(asWebSocketStream(Fs2Streams[IO])(webSocketFramePipe))
        .get(uri"wss://ws.postman-echo.com/raw")
        .send(backend)
        .void
    }
    .unsafeRunSync()
}
