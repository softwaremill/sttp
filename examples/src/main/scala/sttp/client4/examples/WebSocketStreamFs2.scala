package sttp.client4.examples

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import fs2.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.client4.ws.stream.*
import sttp.ws.WebSocketFrame

object WebSocketStreamFs2 extends IOApp:
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

  override def run(args: List[String]): IO[ExitCode] = HttpClientFs2Backend
    .resource[IO]()
    .use { backend =>
      basicRequest
        .get(uri"wss://ws.postman-echo.com/raw")
        .response(asWebSocketStream(Fs2Streams[IO])(webSocketFramePipe))
        .send(backend)
        .void
    }
    .map(_ => ExitCode.Success)
