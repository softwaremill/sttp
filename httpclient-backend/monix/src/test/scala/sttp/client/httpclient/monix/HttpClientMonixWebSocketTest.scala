package sttp.client.httpclient.monix

import java.util.concurrent.LinkedBlockingQueue

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client._
import sttp.client.impl.monix.{MonixWebSockets, TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

import scala.collection.JavaConverters._

class HttpClientMonixWebSocketTest extends WebSocketTest[Task] with WebSocketStreamingTest[Task, MonixStreams] {
  implicit val backend: SttpBackend[Task, MonixStreams with WebSockets] =
    HttpClientMonixBackend().runSyncUnsafe()
  implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  implicit val monad: MonadError[Task] = TaskMonadAsyncError
  override val streams: MonixStreams = MonixStreams

  override def functionToPipe(
      initial: List[WebSocketFrame.Data[_]],
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    in => Observable.fromIterable(initial) ++ in.concatMapIterable(m => f(m).toList)

  it should "use pipe to process websocket messages - server-terminated - text accumulator" in {
    val received = new LinkedBlockingQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_expect_echo")
      .response(
        asWebSocketStreamAlways(streams)(
          MonixWebSockets.fromTextPipe { wholePayload =>
            received.add(wholePayload)
            WebSocketFrame.text(wholePayload + "-echo")
          }
        )
      )
      .send(backend)
      .map { _ =>
        received.asScala.toList shouldBe List("test1", "test2", "test3")
      }
      .toFuture()
  }

  it should "use pipe to process websocket messages - client-terminated - text accumulator" in {
    val received = new LinkedBlockingQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(
        asWebSocketStreamAlways(streams)(
          MonixWebSockets
            .fromTextPipe { wholePayload =>
              received.add(wholePayload)
              if (wholePayload == "echo: 5")
                WebSocketFrame.close
              else {
                WebSocketFrame.text((wholePayload.substring(6).toInt + 1).toString)
              }
            }
            .andThen(rest => Observable.now(WebSocketFrame.text("1")) ++ rest)
        )
      )
      .send(backend)
      .map { _ =>
        received.asScala.toList shouldBe List("echo: 1", "echo: 2", "echo: 3", "echo: 4", "echo: 5")
      }
      .toFuture()
  }
}
