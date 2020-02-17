package sttp.client.testing.websocket

import java.util.concurrent.ConcurrentLinkedQueue

import com.github.ghik.silencer.silent
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.Assertion
import sttp.client._
import sttp.client.monad.MonadError
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}
import sttp.client.monad.syntax._

import scala.collection.JavaConverters._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}

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
  def testErrorWhenEndpointIsNotWebsocket: Boolean = true
  def createHandler(onTextFrame: String => Unit): WS_HANDLER[WS]
  def sendText(ws: WS, t: String): Unit
  def sendCloseFrame(ws: WS): Unit

  it should "send and receive ten messages" in {
    implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(300, Millis)), interval = scaled(Span(15, Millis)))

    val n = 10
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(received.add))
      .map { response =>
        (1 to n).foreach { i =>
          sendText(response.result, s"test$i")
        }
        eventually {
          received.asScala.toList shouldBe (1 to n).map(i => s"echo: test$i").toList
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
  if (testErrorWhenEndpointIsNotWebsocket) {
    it should "error if the endpoint is not a websocket" in {
      monad
        .handleError(
          basicRequest
            .get(uri"$wsEndpoint/echo")
            .openWebsocket(createHandler(_ => ()))
            .map(_ => fail("An exception should be thrown"): Assertion)
        ) {
          case e => (e shouldBe a[SttpClientException]).unit
        }
        .toFuture()
    }
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture
    super.afterAll()
  }
}
