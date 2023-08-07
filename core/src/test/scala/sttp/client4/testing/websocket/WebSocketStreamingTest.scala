package sttp.client4.testing.websocket

import org.scalatest.Suite
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.client4.testing.HttpTest.wsEndpoint
import sttp.client4.testing.{ConvertToFuture, ToFutureWrapper}
import sttp.client4._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.WebSocketFrame

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.collection.JavaConverters._

trait WebSocketStreamingTest[F[_], S] extends ToFutureWrapper { outer: Suite with AsyncFlatSpecLike with Matchers =>
  val streams: Streams[S]
  val backend: WebSocketStreamBackend[F, S]
  implicit def monad: MonadError[F]
  implicit val convertToFuture: ConvertToFuture[F]

  def webSocketPipeTerminatedByServerTest(
      postfix: String
  )(pipe: ConcurrentLinkedQueue[String] => streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]) =
    it should s"use pipe to process websocket messages - server-terminated - $postfix" in {
      val received = new ConcurrentLinkedQueue[String]()
      basicRequest
        .get(uri"$wsEndpoint/ws/send_and_expect_echo")
        .response(asWebSocketStreamAlways(streams)(pipe(received)))
        .send(backend)
        .map { _ =>
          received.asScala.toList shouldBe List("test1", "test2", "test3")
        }
        .toFuture()
    }

  def webSocketPipeClientTerminated(
      postfix: String
  )(pipe: ConcurrentLinkedQueue[String] => streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]) =
    it should s"use pipe to process websocket messages - client-terminated - $postfix" in {
      val received = new ConcurrentLinkedQueue[String]()
      basicRequest
        .get(uri"$wsEndpoint/ws/echo")
        .response(
          asWebSocketStreamAlways(streams)(pipe(received))
        )
        .send(backend)
        .map { _ =>
          received.asScala.toList shouldBe List("echo: 1", "echo: 2", "echo: 3", "echo: 4", "echo: 5")
        }
        .toFuture()
    }

  webSocketPipeTerminatedByServerTest("raw") { received =>
    val buffer = new AtomicReference[String]("")
    functionToPipe {
      case WebSocketFrame.Text(payload, false, _) =>
        val s = buffer.get()
        buffer.set(s + payload)
        None
      case WebSocketFrame.Text(payload, _, _) =>
        val wholePayload = buffer.getAndSet("") + payload
        received.add(wholePayload)
        Some(WebSocketFrame.text(wholePayload + "-echo"))
      case _ => throw new RuntimeException()
    }
  }

  webSocketPipeTerminatedByServerTest("fromTextPipe") { received =>
    fromTextPipe { wholePayload =>
      received.add(wholePayload)
      WebSocketFrame.text(wholePayload + "-echo")
    }
  }

  webSocketPipeClientTerminated("raw") { received =>
    val buffer = new AtomicReference[String]("")
    prepend(WebSocketFrame.text("1"))(functionToPipe {
      case WebSocketFrame.Text(payload, false, _) =>
        val s = buffer.get()
        buffer.set(s + payload)
        None
      case WebSocketFrame.Text(payload, _, _) =>
        val wholePayload = buffer.getAndSet("") + payload
        received.add(wholePayload)
        if (wholePayload == "echo: 5")
          Some(WebSocketFrame.close)
        else
          Some(WebSocketFrame.text((wholePayload.substring(6).toInt + 1).toString))
      case _ => throw new RuntimeException()
    })
  }

  webSocketPipeClientTerminated("fromTextPipe") { received =>
    prepend(item = WebSocketFrame.text("1"))(to = fromTextPipe { wholePayload =>
      received.add(wholePayload)
      if (wholePayload == "echo: 5")
        WebSocketFrame.close
      else {
        WebSocketFrame.text((wholePayload.substring(6).toInt + 1).toString)
      }
    })
  }

  def prepend(item: WebSocketFrame.Text)(
      to: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]

  def fromTextPipe(function: String => WebSocketFrame): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]

  def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
}
