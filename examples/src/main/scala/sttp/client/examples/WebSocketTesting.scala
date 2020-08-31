package sttp.client.examples

import monix.eval.Task
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client._
import sttp.client.asynchttpclient.monix.AsyncHttpClientMonixBackend
import sttp.client.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.ws.{WebSocket, WebSocketFrame}
import sttp.ws.testing.WebSocketStub

object WebSocketTesting extends App {
  // the web socket-handling logic
  def useWebSocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    def receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED [$t]")))
    for {
      _ <- send(1)
      _ <- send(2)
      _ <- receive
      _ <- receive
    } yield ()
  }

  // the request description
  def openWebSocket(backend: SttpBackend[Task, WebSockets]): Task[Unit] = {
    basicRequest
      .response(asWebSocket(useWebSocket))
      .get(uri"wss://echo.websocket.org")
      .send(backend)
      .void
  }

  // the backend stub which we'll use instead of a "real" backend
  val stubBackend: SttpBackendStub[Task, MonixStreams with WebSockets] =
    AsyncHttpClientMonixBackend.stub
      .whenRequestMatches(_.uri.toString().contains("echo.websocket.org"))
      .thenRespond(
        WebSocketStub.noInitialReceive.thenRespond {
          case WebSocketFrame.Text(payload, _, _) =>
            List(WebSocketFrame.text(s"response to: $payload"))
          case _ => Nil // ignoring other types of messages
        },
        StatusCode.SwitchingProtocols
      )

  // running the test
  import monix.execution.Scheduler.Implicits.global
  openWebSocket(stubBackend).runSyncUnsafe()
}
