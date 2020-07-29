package sttp.client.testing.websocket

import org.scalatest.{Assertion, BeforeAndAfterAll, SuiteMixin}
import org.scalatest.concurrent.{Signaler, ThreadSignaler, TimeLimits}
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import sttp.client._
import sttp.client.SttpClientException.ReadException
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.testing.{ConvertToFuture, ToFutureWrapper}
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame

import scala.concurrent.duration.FiniteDuration

abstract class WebSocketTest[F[_]]
    extends SuiteMixin
    with AsyncFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ToFutureWrapper
    with TimeLimits {

  implicit val backend: SttpBackend[F, WebSockets]
  implicit val convertToFuture: ConvertToFuture[F]
  implicit val monad: MonadError[F]

  it should "send and receive three messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { ws: WebSocket[F] =>
        send(ws, 3) >>
          receiveEcho(ws, 3) >>
          ws.close >>
          succeed.unit
      })
      .send()
      .map(_ => succeed)
      .toFuture()
  }

  it should "send and receive two messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketUnsafeAlways[F])
      .send()
      .flatMap { response =>
        val ws = response.body
        send(ws, 2) >>
          receiveEcho(ws, 2) >>
          ws.close >>
          succeed.unit
      }
      .toFuture()
  }

  it should "send and receive 1000 messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketUnsafeAlways[F])
      .send()
      .flatMap { response =>
        val ws = response.body
        send(ws, 1000) >>
          receiveEcho(ws, 1000) >>
          ws.close >>
          succeed.unit
      }
      .toFuture()
  }

  it should "receive two messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_wait")
      .response(asWebSocketUnsafeAlways[F])
      .send()
      .flatMap { response =>
        val ws = response.body
        ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test10"))) >>
          ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test20"))) >>
          ws.close.map(_ => succeed)
      }
      .toFuture()
  }

  it should "fail correctly when can't open web socket (unsafe)" in {
    implicit val signaler: Signaler = ThreadSignaler
    failAfter(Span(15, Seconds)) {
      basicRequest
        .get(uri"$wsEndpoint/ws/404")
        .response(asWebSocketUnsafeAlways[F])
        .send()
        .map(_ => fail("should not open WebSocket"))
        .handleError {
          case _: ReadException => monad.unit(succeed)
        }
        .toFuture()
    }
  }

  it should "fail correctly when can't open web socket" in {
    implicit val signaler: Signaler = ThreadSignaler
    failAfter(Span(15, Seconds)) {
      basicRequest
        .get(uri"$wsEndpoint/ws/404")
        .response(asWebSocketUnsafe[F])
        .send()
        .map {
          _.body.isLeft shouldBe true
        }
        .toFuture()
    }
  }

  def send(ws: WebSocket[F], count: Int): F[Unit] = {
    val fs = (1 to count).map(i => ws.send(WebSocketFrame.text(s"test$i")))
    fs.foldLeft(().unit)(_ >> _)
  }

  def receiveEcho(ws: WebSocket[F], count: Int): F[Assertion] = {
    val fs = (1 to count).map(i => () => ws.receiveText().map(_ shouldBe Right(s"echo: test$i")))
    fs.foldLeft(succeed.unit)((f1, lazy_f2) => f1.flatMap(_ => lazy_f2()))
  }

  // TODO def eventually[T](interval: FiniteDuration, attempts: Int)(f: => F[T]): F[T]

  override protected def afterAll(): Unit = {
    backend.close().toFuture()
    super.afterAll()
  }
}
