package sttp.client4.integ.ox.ws

import ox.*
import ox.channels.Source
import ox.channels.Sink
import sttp.client4.*
import sttp.client4.integ.ox.ws.*
import sttp.client4.testing.HttpTest.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import sttp.client4.DefaultSyncBackend
import sttp.client4.ws.SyncWebSocket
import sttp.client4.ws.sync.*
import sttp.ws.WebSocketFrame

class OxWebSocketTest extends AnyFlatSpec with BeforeAndAfterAll with Matchers:
  val backend = DefaultSyncBackend()

  it should "send and receive three messages using asWebSocketAlways" in supervised {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { (ws: SyncWebSocket) =>
        val (wsSource, wsSink) = asSourceAndSink(ws)
        sendText(wsSink, 3)
        receiveEchoText(wsSource, 3)
        ws.close()
      })
      .send(backend)
  }
  def sendText(ws: Sink[WebSocketFrame], count: Int)(using Ox): Unit =
    Source.fromIterable(1 to count).map(i => WebSocketFrame.text(s"test$i")).pipeTo(ws)

  def receiveEchoText(ws: Source[WebSocketFrame], count: Int): Unit =
    for (i <- 1 to count)
      ws.receive() match
        case WebSocketFrame.Text(t, _, _) => t shouldBe s"echo: test$i"
        case f                            => fail(s"Unexpected frame: $f")
