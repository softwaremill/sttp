package sttp.client.testing.websocket

import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

import com.github.ghik.silencer.silent
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.monad.MonadError
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}
import sttp.client.monad.syntax._

import scala.collection.JavaConverters._

@silent("discarded")
trait LowLevelListenerWebSocketTest[F[_], WS, WS_HANDLER[_]]
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {

  implicit def backend: SttpBackend[F, Nothing, WS_HANDLER]
  implicit def convertToFuture: ConvertToFuture[F]
  private implicit lazy val monad: MonadError[F] = backend.responseMonad

  def createHandler(onTextFrame: String => Unit): WS_HANDLER[WS]
  def sendText(ws: WS, t: String): Unit
  def sendCloseFrame(ws: WS): Unit

  it should "send and receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(received.add))
      .map { response =>
        sendText(response.result, "test1")
        sendText(response.result, "test2")
        eventually {
          received.asScala.toList shouldBe List("echo: test1", "echo: test2")
        }
        sendCloseFrame(response.result)
        succeed
      }
      .toFuture
  }

  it should "receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocket(createHandler(received.add))
      .map { _ =>
        eventually {
          received.asScala.toList shouldBe List("test10", "test20")
        }
      }
      .toFuture
  }

  it should "error if the endpoint is not a websocket" in {
    monad
      .handleError(
        basicRequest
          .get(uri"$wsEndpoint/echo")
          .openWebsocket(createHandler(_ => ()))
          .map(_ => fail("An exception should be thrown"): Assertion)
      ) {
        case e => (e shouldBe a[IOException]).unit
      }
      .toFuture()
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture
    super.afterAll()
  }
}
