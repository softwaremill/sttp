// {cat=Testing; effects=cats-effect; backend=HttpClient}: Create a backend stub which simulates interactions with a WebSocket

//> using dep com.softwaremill.sttp.client4::fs2:4.0.9

package sttp.client4.examples.testing

import sttp.client4.*
import sttp.client4.ws.async.*
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.model.StatusCode
import sttp.ws.{WebSocket, WebSocketFrame}
import sttp.ws.testing.WebSocketStub
import cats.effect.IOApp
import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import cats.effect.ExitCode

object WebSocketTesting extends IOApp:
  // the web socket-handling logic
  def useWebSocket(ws: WebSocket[IO]): IO[Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    val receive = ws.receiveText().flatMap(t => IO(println(s"RECEIVED [$t]")))
    send(1) *> send(2) *> receive *> receive
  }

  // the request description
  def openWebSocket(backend: WebSocketBackend[IO]): IO[Unit] =
    basicRequest
      .get(uri"wss://echo.websocket.org")
      .response(asWebSocket(useWebSocket))
      .send(backend)
      .void

  // the backend stub which we'll use instead of a "real" backend
  val stubBackend: WebSocketStreamBackendStub[IO, Fs2Streams[IO]] =
    HttpClientFs2Backend
      .stub[IO]
      .whenRequestMatches(_.uri.toString().contains("echo.websocket.org"))
      .thenRespondAdjust(
        WebSocketStub.noInitialReceive.thenRespond {
          case WebSocketFrame.Text(payload, _, _) =>
            List(WebSocketFrame.text(s"response to: $payload"))
          case _ => Nil // ignoring other types of messages
        },
        StatusCode.SwitchingProtocols
      )

  // running the test
  override def run(args: List[String]): IO[ExitCode] =
    openWebSocket(stubBackend).map(_ => ExitCode.Success)
