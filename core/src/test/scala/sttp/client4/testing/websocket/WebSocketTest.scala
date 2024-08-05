package sttp.client4.testing.websocket

import org.scalatest.concurrent.{Signaler, ThreadSignaler, TimeLimits}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{Assertion, BeforeAndAfterAll}
import sttp.client4.SttpClientException.ReadException
import sttp.client4._
import sttp.client4.logging.{LogConfig, LogLevel, Logger, LoggingBackend}
import sttp.client4.testing.HttpTest.wsEndpoint
import sttp.client4.testing.{ConvertToFuture, ToFutureWrapper}
import sttp.client4.ws.async._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketFrame}

import java.util.concurrent.atomic.AtomicInteger

abstract class WebSocketTest[F[_]]
    extends AsyncFlatSpec
    with BeforeAndAfterAll
    with Matchers
    with ToFutureWrapper
    with TimeLimits {

  val backend: WebSocketBackend[F]
  implicit val convertToFuture: ConvertToFuture[F]
  implicit def monad: MonadError[F]

  def throwsWhenNotAWebSocket: Boolean = false
  def supportsReadingWebSocketResponseHeaders: Boolean = true

  it should "send and receive three messages using asWebSocketAlways" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { (ws: WebSocket[F]) =>
        for {
          _ <- sendText(ws, 3)
          _ <- receiveEchoText(ws, 3)
          _ <- ws.close()
        } yield succeed
      })
      .send(backend)
      .map(_ => succeed)
      .toFuture()
  }

  it should "send and receive three messages using asWebSocket" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocket { (ws: WebSocket[F]) =>
        for {
          _ <- sendText(ws, 3)
          _ <- receiveEchoText(ws, 3)
          _ <- ws.close()
        } yield succeed
      })
      .send(backend)
      .map(_ => succeed)
      .toFuture()
  }

  it should "send and receive binary messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { (ws: WebSocket[F]) =>
        for {
          _ <- sendBinary(ws, 3)
          _ <- receiveEchoBinary(ws, 3)
          _ <- ws.close()
        } yield succeed
      })
      .send(backend)
      .map(_ => succeed)
      .toFuture()
  }

  it should "send and receive two messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlwaysUnsafe[F])
      .send(backend)
      .flatMap { response =>
        val ws = response.body

        for {
          _ <- sendText(ws, 2)
          _ <- receiveEchoText(ws, 2)
          _ <- ws.close()
        } yield succeed
      }
      .toFuture()
  }

  it should "send and receive 1000 messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlwaysUnsafe[F])
      .send(backend)
      .flatMap { response =>
        val ws = response.body

        for {
          _ <- sendText(ws, 1000)
          _ <- receiveEchoText(ws, 1000)
          _ <- ws.close()
        } yield succeed
      }
      .toFuture()
  }

  it should "receive two messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_wait")
      .response(asWebSocketAlwaysUnsafe[F])
      .send(backend)
      .flatMap { response =>
        val ws = response.body
        for {
          _ <- ws.receive().map(_ shouldBe WebSocketFrame.text("test10"))
          _ <- ws.receive().map(_ shouldBe WebSocketFrame.text("test20"))
          _ <- ws.close()
        } yield succeed
      }
      .toFuture()
  }

  it should "fail correctly when can't open web socket (always, unsafe)" in {
    implicit val signaler: Signaler = ThreadSignaler
    failAfter(Span(15, Seconds)) {
      basicRequest
        .get(uri"$wsEndpoint/ws/404")
        .response(asWebSocketAlwaysUnsafe[F])
        .send(backend)
        .map(_ => fail("should not open WebSocket"))
        .handleError { case _: ReadException =>
          monad.unit(succeed)
        }
        .toFuture()
    }
  }

  it should "fail correctly when can't open web socket (unsafe)" in {
    implicit val signaler: Signaler = ThreadSignaler
    failAfter(Span(15, Seconds)) {
      basicRequest
        .get(uri"$wsEndpoint/ws/404")
        .response(asWebSocketUnsafe[F])
        .send(backend)
        .map {
          _.body.isLeft shouldBe true
        }
        .handleError {
          case _: ReadException if throwsWhenNotAWebSocket => succeed.unit
        }
        .toFuture()
    }
  }

  class TestLogger extends Logger[F] {
    val msgCounter = new AtomicInteger()
    val errCounter = new AtomicInteger()

    override def apply(level: LogLevel, message: => String, context: Map[String, Any]): F[Unit] =
      monad.unit(println(message)).map(_ => msgCounter.incrementAndGet())
    override def apply(level: LogLevel, message: => String, t: Throwable, context: Map[String, Any]): F[Unit] =
      monad.unit(println(message + t.toString)).map(_ => errCounter.incrementAndGet())
  }

  it should "work with LoggingBackend" in {
    val logger = new TestLogger()
    val loggingBackend = LoggingBackend(backend, logger)
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { (ws: WebSocket[F]) =>
        for {
          _ <- sendText(ws, 3)
          _ <- receiveEchoText(ws, 3)
          _ <- ws.close()
        } yield succeed
      })
      .send(loggingBackend)
      .map { _ =>
        logger.msgCounter.get() shouldBe 2
        logger.errCounter.get() shouldBe 0
      }
      .toFuture()
  }

  it should "work with LoggingBackend with logResponseBody" in {
    val logger = new TestLogger()
    val loggingBackend = LoggingBackend(backend, logger, LogConfig(logResponseBody = true))
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { (ws: WebSocket[F]) =>
        for {
          _ <- sendText(ws, 3)
          _ <- receiveEchoText(ws, 3)
          _ <- ws.close()
        } yield succeed
      })
      .send(loggingBackend)
      .map { _ =>
        logger.msgCounter.get() shouldBe 2
        logger.errCounter.get() shouldBe 0
      }
      .toFuture()
  }

  if (supportsReadingWebSocketResponseHeaders) {
    it should "receive the extra headers set by the server" in {
      basicRequest
        .get(uri"$wsEndpoint/ws/header")
        .response(asWebSocketAlways((ws: WebSocket[F]) => ws.close()))
        .send(backend)
        .map { response =>
          response.header("Correlation-id") shouldBe Some("ABC-XYZ-123")
        }
        .toFuture()
    }
  }

  def sendText(ws: WebSocket[F], count: Int): F[Unit] =
    send(ws, count, (i: Int) => WebSocketFrame.text(s"test$i"))

  def sendBinary(ws: WebSocket[F], count: Int): F[Unit] =
    send(ws, count, (i: Int) => WebSocketFrame.binary(Array(i.toByte)))

  def receiveEchoText(ws: WebSocket[F], count: Int): F[Assertion] =
    receiveEcho(count, (i: Int) => ws.receiveText().map(_ shouldBe s"echo: test$i"))

  def receiveEchoBinary(ws: WebSocket[F], count: Int): F[Assertion] =
    receiveEcho(count, (i: Int) => ws.receiveBinary(false).map(_ shouldBe Array(i.toByte)))

  private def send(ws: WebSocket[F], count: Int, frame: Int => WebSocketFrame): F[Unit] = {
    val fs = (1 to count).map(i => () => ws.send(frame(i)))
    fs.foldLeft(().unit)((f1, lazy_f2) => f1.flatMap(_ => lazy_f2()))
  }

  private def receiveEcho(count: Int, assert: Int => F[Assertion]): F[Assertion] = {
    val fs = (1 to count).map(i => () => assert(i))
    fs.foldLeft(succeed.unit)((f1, lazy_f2) => f1.flatMap(_ => lazy_f2()))
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture()
    super.afterAll()
  }
}
