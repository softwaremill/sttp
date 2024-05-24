package sttp.client4.impl.ox.ws

import org.scalatest.BeforeAndAfterAll
import org.scalatest.EitherValues
import org.scalatest.compatible.Assertion
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.*
import ox.channels.ChannelClosed
import ox.channels.Sink
import ox.channels.Source
import sttp.client4.*
import sttp.client4.DefaultSyncBackend
import sttp.client4.logging.LogLevel
import sttp.client4.logging.Logger
import sttp.client4.logging.LoggingBackend
import sttp.client4.testing.HttpTest.*
import sttp.client4.ws.sync.*
import sttp.model.StatusCode
import sttp.ws.WebSocketFrame
import sttp.ws.testing.WebSocketStub

import java.util.concurrent.atomic.AtomicInteger
import scala.util.Failure
import scala.util.Success

class OxWebSocketTest extends AnyFlatSpec with BeforeAndAfterAll with Matchers with EitherValues with Eventually:
  lazy val backend: WebSocketSyncBackend = DefaultSyncBackend()

  behavior of "SyncWebSocket.asSourceAndSink"

  it should "send and receive three messages using asWebSocketAlways" in supervised {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { ws =>
        val (wsSource, wsSink) = asSourceAndSink(ws)
        sendText(wsSink, 3)
        receiveEchoText(wsSource, 3)
        eventually(expectClosed(wsSource, wsSink))
      })
      .send(backend)
  }

  it should "send and receive three messages using asWebSocket" in supervised {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocket { ws =>
        val (wsSource, wsSink) = asSourceAndSink(ws)
        sendText(wsSink, 3)
        receiveEchoText(wsSource, 3)
        eventually(expectClosed(wsSource, wsSink))
      })
      .send(backend)
  }

  it should "close response source if request sink fails" in supervised {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocket { ws =>
        val (wsSource, wsSink) = asSourceAndSink(ws)
        wsSink.send(WebSocketFrame.text("test1"))
        wsSink.error(new Exception("failed source"))
        eventually(wsSource.isClosedForReceiveDetail shouldBe Some(ChannelClosed.Done))
      })
      .send(backend)
  }

  it should "close request sink if response source fails" in supervised {
    val expectedException = new Exception("test exception")
    val stubBackend: WebSocketSyncBackend =
      DefaultSyncBackend.stub
        .whenRequestMatches(_.uri.toString().contains("echo.websocket.org"))
        .thenRespond(
          WebSocketStub
            .initialReceiveWith(List(Success(WebSocketFrame.text("first response")), Failure(expectedException))),
          StatusCode.SwitchingProtocols
        )
    basicRequest
      .get(uri"ws://echo.websocket.org")
      .response(asWebSocket { ws =>
        val (wsSource, wsSink) = asSourceAndSink(ws)
        eventually(wsSource.isClosedForReceiveDetail shouldBe Some(ChannelClosed.Error(expectedException)))
        wsSink.isClosedForSendDetail shouldBe Some(ChannelClosed.Done)
      })
      .send(stubBackend)
  }

  it should "pong on ping" in supervised {
    val stubBackend: WebSocketSyncBackend =
      DefaultSyncBackend.stub
        .whenRequestMatches(_.uri.toString().contains("echo.websocket.org"))
        .thenRespond(
          WebSocketStub
            .initialReceive(List.fill(50)(WebSocketFrame.Ping("test-ping".getBytes)))
            .thenRespond {
              case WebSocketFrame.Pong(payload) if new String(payload) == "test-ping" =>
                List(WebSocketFrame.text("test"))
              case other => 
                fail(s"Unexpected frame: $other")
            },
          StatusCode.SwitchingProtocols
        )
    basicRequest
      .get(uri"ws://echo.websocket.org")
      .response(asWebSocket { ws =>
        val (wsSource, wsSink) = asSourceAndSink(ws)
        wsSource.receiveOrClosed() shouldBe WebSocketFrame.text("test")
      })
      .send(stubBackend)
  }

  it should "receive fragmented frames if concatenateFragmented = false" in supervised {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocket { ws =>
        val (wsSource, wsSink) = asSourceAndSink(ws, concatenateFragmented = false)
        sendText(wsSink, 1)
        wsSource.take(3).toList shouldBe List(
          WebSocketFrame.Text("echo: ", false, None),
          WebSocketFrame.Text("test1", false, None),
          WebSocketFrame.Text("", true, None)
        )
        eventually(expectClosed(wsSource, wsSink))
      })
      .send(backend)
  }

  it should "send and receive binary messages" in supervised {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocket { ws =>
        val (wsSource, wsSink) = asSourceAndSink(ws)
        sendBinary(wsSink, 3)
        receiveEchoBinary(wsSource, 3)
        eventually(expectClosed(wsSource, wsSink))
      })
      .send(backend)
  }

  class TestLogger extends Logger[Identity]:
    val msgCounter = new AtomicInteger()
    val errCounter = new AtomicInteger()

    override def apply(level: LogLevel, message: => String, context: Map[String, Any]): Unit =
      msgCounter.incrementAndGet().discard
    override def apply(level: LogLevel, message: => String, t: Throwable, context: Map[String, Any]): Unit =
      errCounter.incrementAndGet().discard

  it should "work with LoggingBackend" in supervised {
    val logger = new TestLogger()
    val loggingBackend = LoggingBackend(backend, logger)
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { ws =>
        val (wsSource, wsSink) = asSourceAndSink(ws)
        sendText(wsSink, 3)
        receiveEchoText(wsSource, 3)
      })
      .send(loggingBackend)
    logger.msgCounter.get() shouldBe 2
    logger.errCounter.get() shouldBe 0
  }

  def sendText(wsSink: Sink[WebSocketFrame], count: Int)(using Ox): Unit =
    Source.fromIterable(1 to count).map(i => WebSocketFrame.text(s"test$i")).pipeTo(wsSink)

  def sendBinary(wsSink: Sink[WebSocketFrame], count: Int)(using Ox): Unit =
    Source.fromIterable(1 to count).map(i => WebSocketFrame.binary(Array(i.toByte))).pipeTo(wsSink)

  def receiveEchoText(wsSource: Source[WebSocketFrame], count: Int): Unit =
    for (i <- 1 to count)
      wsSource.receive() match
        case WebSocketFrame.Text(t, _, _) => t shouldBe s"echo: test$i"
        case f                            => fail(s"Unexpected frame: $f")

  def receiveEchoBinary(wsSource: Source[WebSocketFrame], count: Int): Unit =
    for (i <- 1 to count)
      wsSource.receive() match
        case WebSocketFrame.Binary(bs, _, _) => bs shouldBe Array(i.toByte)
        case f                               => fail(s"Unexpected frame: $f")

  def expectClosed(wsSource: Source[WebSocketFrame], wsSink: Sink[WebSocketFrame]): Assertion =
    wsSink.isClosedForSendDetail shouldBe Some(ChannelClosed.Done)
    wsSource.isClosedForReceiveDetail shouldBe Some(ChannelClosed.Done)

  override protected def afterAll(): Unit =
    backend.close()
    super.afterAll()
