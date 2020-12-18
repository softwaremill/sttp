package sttp.client3

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.sse.ServerSentEvent

class ServerSentEventTest extends AnyFlatSpec with Matchers {
  val data = List(
    (List(": this is a test stream"), ServerSentEvent()),
    (List("data: some text"), ServerSentEvent(Some("some text"))),
    (List("data:  some text"), ServerSentEvent(Some(" some text"))),
    (List("data: another message", "data: with two lines"), ServerSentEvent(Some("another message\nwith two lines"))),
    (
      List("event: userconnect", "data: {\"username\": \"bobby\", \"time\": \"02:33:48\"}"),
      ServerSentEvent(Some("{\"username\": \"bobby\", \"time\": \"02:33:48\"}"), Some("userconnect"))
    ),
    (
      List("data:second event", "id"),
      ServerSentEvent(Some("second event"), id = Some(""))
    ),
    (
      List("data:x", "retry:50"),
      ServerSentEvent(Some("x"), retry = Some(50))
    )
  )

  for ((lines, expected) <- data) {
    it should s"parse ${lines.size} lines starting with ${lines.headOption.getOrElse("-")}" in {
      ServerSentEvent.parse(lines) shouldBe expected
    }
  }
}
