package sttp.client.testing.websocket

import java.util.concurrent.ConcurrentLinkedQueue

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, BeforeAndAfterAll}
import sttp.client._
import sttp.client.monad.MonadError
import sttp.client.testing.{ConvertToFuture, ToFutureWrapper}
import sttp.client.monad.syntax._

import scala.collection.JavaConverters._
import org.scalatest.SuiteMixin
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.client.testing.HttpTest.wsEndpoint

// TODO: change to `extends AsyncFlatSpec` when https://github.com/scalatest/scalatest/issues/1802 is fixed
trait LowLevelListenerWebSocketTest[F[_], WS, WS_HANDLER[_]]
    extends SuiteMixin
    with AsyncFlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {

  implicit def backend: SttpBackend[F, Any, WS_HANDLER]
  implicit def convertToFuture: ConvertToFuture[F]
  private implicit lazy val monad: MonadError[F] = backend.responseMonad
  def testErrorWhenEndpointIsNotWebsocket: Boolean = true
  def createHandler(onTextFrame: String => Unit): WS_HANDLER[WS]
  def sendText(ws: WS, t: String): Unit
  def sendCloseFrame(ws: WS): Unit

  it should "send and receive ten messages" in {
    val n = 10
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(received.add))
      .map { response =>
        (1 to n).foreach { i =>
          val msg = s"test$i"
          info(s"Sending text message: $msg")
          sendText(response.result, msg)
        }
        eventually {
          received.asScala.toList shouldBe (1 to n).map(i => s"echo: test$i").toList
        }
        sendCloseFrame(response.result)
        succeed
      }
      .toFuture()
  }

  it should "receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_wait")
      .openWebsocket(createHandler(received.add))
      .map { response =>
        eventually {
          received.asScala.toList shouldBe List("test10", "test20")
        }
        sendCloseFrame(response.result)
        succeed
      }
      .toFuture()
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
          case e => (e shouldBe a[SttpClientException.ReadException]).unit
        }
        .toFuture()
    }
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture()
    super.afterAll()
  }
}
