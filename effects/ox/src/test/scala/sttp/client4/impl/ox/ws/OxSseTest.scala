package sttp.client4.impl.ox.sse

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.client4.testing.HttpTest.*
import sttp.model.sse.ServerSentEvent

class OxServerSentEventsTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll:

  lazy val backend: WebSocketSyncBackend = DefaultSyncBackend()

  behavior of "OxServerSentEvents"

  it should "parse SSEs" in {
    val sseData = "text1 in line1\ntext2 in line2"
    val expectedEvent = ServerSentEvent(data = Some(sseData), eventType = Some("test-event"), retry = Some(42000))
    val expectedEvents =
      Seq(expectedEvent.copy(id = Some("1")), expectedEvent.copy(id = Some("2")), expectedEvent.copy(id = Some("3")))
    basicRequest
      .post(uri"$endpoint/sse/echo3")
      .body(sseData)
      .response(asInputStreamAlways { is =>
        OxServerSentEvents.parse(is).take(3).runToList() shouldBe expectedEvents
        ()
      })
      .send(backend)
  }

  override protected def afterAll(): Unit =
    backend.close()
    super.afterAll()
