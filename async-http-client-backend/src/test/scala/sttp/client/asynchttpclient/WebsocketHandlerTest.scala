package sttp.client.asynchttpclient

import java.io.IOException

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame

abstract class WebsocketHandlerTest[F[_]]
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {

  implicit val backend: SttpBackend[F, Nothing, WebSocketHandler]
  implicit val convertToFuture: ConvertToFuture[F]
  implicit val monad: MonadError[F]

  def createHandler: () => WebSocketHandler[WebSocket[F]]

  it should "send and receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler())
      .flatMap { response =>
        val mws = response.result
        mws.send(WebSocketFrame.text("test1")) >>
          mws.send(WebSocketFrame.text("test2")) >>
          mws.receive.map(_ shouldBe Right(WebSocketFrame.text("echo: test1"))) >>
          mws.receive.map(_ shouldBe Right(WebSocketFrame.text("echo: test2"))) >>
          mws.close >>
          succeed.unit
      }
      .toFuture
  }

  it should "receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocket(createHandler())
      .flatMap { response =>
        val mws = response.result
        mws.receive.map(_ shouldBe Right(WebSocketFrame.text("test10"))) >>
          mws.receive.map(_ shouldBe Right(WebSocketFrame.text("test20"))) >>
          mws.receive.map(_ shouldBe 'left)
      }
      .toFuture
  }

  it should "error if the endpoint is not a websocket" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/echo")
          .openWebsocket(createHandler())
          .map(_ => fail: Assertion)
      } {
        case e: Exception => (e shouldBe a[IOException]).unit
      }
      .toFuture()
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture
    super.afterAll()
  }
}
