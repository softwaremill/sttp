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

abstract class HighLevelWebsocketTest[F[_], WS_HANDLER[_]]
    extends SuiteMixin
    with AsyncFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ToFutureWrapper
    with TimeLimits {

  implicit val backend: SttpBackend[F, Nothing, WS_HANDLER]
  implicit val convertToFuture: ConvertToFuture[F]
  implicit val monad: MonadError[F]

  def createHandler: Option[Int] => F[WS_HANDLER[WebSocket[F]]]

  it should "send and receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocketF(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        send(ws, 2) >>
          receiveEcho(ws, 2) >>
          ws.close >>
          succeed.unit
      }
      .toFuture()
  }

  it should "send and receive 1000 messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocketF(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        send(ws, 1000) >>
          receiveEcho(ws, 1000) >>
          ws.close >>
          succeed.unit
      }
      .toFuture()
  }

  it should "receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_wait")
      .openWebsocketF(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test10"))) >>
          ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test20"))) >>
          ws.close.map(_ => succeed)
      }
      .toFuture()
  }

  it should "fail correctly when can't open WebSocket" in {
    implicit val signaler: Signaler = ThreadSignaler
    failAfter(Span(15, Seconds)) {
      basicRequest
        .get(uri"$wsEndpoint/ws/404")
        .openWebsocketF(createHandler(None))
        .map(_ => fail("should not open WebSocket"))
        .handleError {
          case ex: ReadException => monad.unit(succeed)
        }
        .toFuture()
    }
  }

  def send(ws: WebSocket[F], count: Int): F[Unit] = {
    val fs = (1 to count).map(i => ws.send(WebSocketFrame.text(s"test$i")))
    fs.foldLeft(().unit)(_ >> _)
  }

  def receiveEcho(ws: WebSocket[F], count: Int): F[Assertion] = {
    val fs = (1 to count).map(i => ws.receiveText().map(_ shouldBe Right(s"echo: test$i")))
    fs.foldLeft(succeed.unit)(_ >> _)
  }

  def eventually[T](interval: FiniteDuration, attempts: Int)(f: => F[T]): F[T]

  override protected def afterAll(): Unit = {
    backend.close().toFuture()
    super.afterAll()
  }
}
