package sttp.client4.examples

import monix.eval.Task
import sttp.capabilities.monix.MonixStreams
import sttp.client4._
import sttp.client4.httpclient.monix.HttpClientMonixBackend
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.model.StatusCode
import sttp.ws.{WebSocket, WebSocketFrame}
import sttp.ws.testing.WebSocketStub

object WebSocketTesting extends App {
  // the web socket-handling logic
  def useWebSocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.sendText(s"Hello $i!")
    val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED [$t]")))
    send(1) *> send(2) *> receive *> receive
  }

  // the request description
  def openWebSocket(backend: WebSocketBackend[Task]): Task[Unit] =
    basicRequest
      .get(uri"wss://echo.websocket.org")
      .response(asWebSocket(useWebSocket))
      .send(backend)
      .void

  // the backend stub which we'll use instead of a "real" backend
  val stubBackend: WebSocketStreamBackendStub[Task, MonixStreams] =
    HttpClientMonixBackend.stub
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
