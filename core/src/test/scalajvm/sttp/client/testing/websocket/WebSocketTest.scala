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

abstract class WebSocketTest[F[_]]
    extends SuiteMixin
    with AsyncFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ToFutureWrapper
    with TimeLimits {

  val backend: SttpBackend[F, WebSockets]
  implicit val convertToFuture: ConvertToFuture[F]
  implicit val monad: MonadError[F]

  def throwsWhenNotAWebSocket: Boolean = false

  it should "send and receive three messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { ws: WebSocket[F] =>
        for {
          _ <- send(ws, 3)
          _ <- receiveEcho(ws, 3)
          _ <- ws.close
        } yield succeed
      })
      .send(backend)
      .map(_ => succeed)
      .toFuture()
  }

  it should "send and receive two messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketUnsafeAlways[F])
      .send(backend)
      .flatMap { response =>
        val ws = response.body

        for {
          _ <- send(ws, 2)
          _ <- receiveEcho(ws, 2)
          _ <- ws.close
        } yield succeed
      }
      .toFuture()
  }

  it should "send and receive 1000 messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketUnsafeAlways[F])
      .send(backend)
      .flatMap { response =>
        val ws = response.body

        for {
          _ <- send(ws, 1000)
          _ <- receiveEcho(ws, 1000)
          _ <- ws.close
        } yield succeed
      }
      .toFuture()
  }

  it should "receive two messages (unsafe)" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_wait")
      .response(asWebSocketUnsafeAlways[F])
      .send(backend)
      .flatMap { response =>
        val ws = response.body
        for {
          _ <- ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test10")))
          _ <- ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test20")))
          _ <- ws.close
        } yield succeed
      }
      .toFuture()
  }

  it should "fail correctly when can't open web socket (unsafe)" in {
    implicit val signaler: Signaler = ThreadSignaler
    failAfter(Span(15, Seconds)) {
      basicRequest
        .get(uri"$wsEndpoint/ws/404")
        .response(asWebSocketUnsafeAlways[F])
        .send(backend)
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

  def send(ws: WebSocket[F], count: Int): F[Unit] = {
    val fs = (1 to count).map(i => () => ws.send(WebSocketFrame.text(s"test$i")))
    fs.foldLeft(().unit)((f1, lazy_f2) => f1.flatMap(_ => lazy_f2()))
  }

  def receiveEcho(ws: WebSocket[F], count: Int): F[Assertion] = {
    val fs = (1 to count).map(i => () => ws.receiveText().map(_ shouldBe Right(s"echo: test$i")))
    fs.foldLeft(succeed.unit)((f1, lazy_f2) => f1.flatMap(_ => lazy_f2()))
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture()
    super.afterAll()
  }
}
