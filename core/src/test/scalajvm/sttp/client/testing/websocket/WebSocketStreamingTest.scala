package sttp.client.testing.websocket

import java.util.concurrent.LinkedBlockingQueue

import org.scalatest.Suite
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.{Streams, WebSockets}
import sttp.monad.MonadError
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.{ConvertToFuture, ToFutureWrapper}
import sttp.client.{SttpBackend, asWebSocketStreamAlways, basicRequest}
import sttp.client._
import sttp.monad.syntax._
import sttp.ws.WebSocketFrame

import scala.collection.JavaConverters._

trait WebSocketStreamingTest[F[_], S] extends ToFutureWrapper { outer: Suite with AsyncFlatSpecLike with Matchers =>
  val streams: Streams[S]
  val backend: SttpBackend[F, S with WebSockets]
  implicit val monad: MonadError[F]
  implicit val convertToFuture: ConvertToFuture[F]

  it should "use pipe to process websocket messages" in {
    val received = new LinkedBlockingQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_expect_echo")
      .response(asWebSocketStreamAlways(streams)(functionToPipe {
        case WebSocketFrame.Text(payload, _, _) =>
          received.add(payload)
          WebSocketFrame.text(payload + "-echo")
      }))
      .send(backend)
      .map { _ =>
        received.asScala.toList shouldBe List("test1", "test2", "test3")
      }
      .toFuture()
  }

  def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
}
