package sttp.client.testing.websocket

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BinaryOperator

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

  it should "use pipe to process websocket messages - server-terminated" in {
    val received = new LinkedBlockingQueue[String]()
    val buffer = new AtomicReference[String]("")
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_expect_echo")
      .response(
        asWebSocketStreamAlways(streams)(
          functionToPipe(
            Nil,
            {
              case WebSocketFrame.Text(payload, false, _) =>
                buffer.accumulateAndGet(
                  payload,
                  new BinaryOperator[String] {
                    override def apply(t: String, u: String): String = t + u
                  }
                )
                None
              case WebSocketFrame.Text(payload, _, _) =>
                val wholePayload = buffer.getAndSet("") + payload
                received.add(wholePayload)
                Some(WebSocketFrame.text(wholePayload + "-echo"))
            }
          )
        )
      )
      .send(backend)
      .map { _ =>
        received.asScala.toList shouldBe List("test1", "test2", "test3")
      }
      .toFuture()
  }

  it should "use pipe to process websocket messages - client-terminated" in {
    val received = new LinkedBlockingQueue[String]()
    val buffer = new AtomicReference[String]("")
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(
        asWebSocketStreamAlways(streams)(
          functionToPipe(
            List(WebSocketFrame.text("1")),
            {
              case WebSocketFrame.Text(payload, false, _) =>
                buffer.accumulateAndGet(
                  payload,
                  new BinaryOperator[String] {
                    override def apply(t: String, u: String): String = t + u
                  }
                )
                None
              case WebSocketFrame.Text(payload, _, _) =>
                val wholePayload = buffer.getAndSet("") + payload
                received.add(wholePayload)

                if (wholePayload == "echo: 5")
                  Some(WebSocketFrame.close)
                else
                  Some(WebSocketFrame.text((wholePayload.substring(6).toInt + 1).toString))
            }
          )
        )
      )
      .send(backend)
      .map { _ =>
        received.asScala.toList shouldBe List("echo: 1", "echo: 2", "echo: 3", "echo: 4", "echo: 5")
      }
      .toFuture()
  }

  def functionToPipe(
      initial: List[WebSocketFrame.Data[_]],
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
}
