package sttp.client.asynchttpclient.zio

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client._
import sttp.client.testing.WebSocketStub
import sttp.client.impl.zio._
import sttp.client.ws.WebSocketEvent
import sttp.model.Headers
import sttp.model.ws.WebSocketFrame

import scala.util.{Failure, Success}

class WebSocketStubZioTests extends AnyFlatSpec with Matchers with ScalaFutures {

  def mkBackend(webSocketStub: WebSocketStub[_]) =
    AsyncHttpClientZioBackend.stub
      .whenRequestMatches(_ => true)
      .thenRespondWebSocket(Headers(List.empty), webSocketStub)

  "web socket stub" should "return initial Incoming frames on 'receive'" in {
    val frames = List("a", "b", "c").map(WebSocketFrame.text(_))
    val webSocketStub = WebSocketStub.withInitialIncoming(frames)
    implicit val b = mkBackend(webSocketStub)
    val test = for {
      handler <- ZioWebSocketHandler()
      ws <- basicRequest.get(uri"http://example.org/a/b/c").openWebsocket(handler).map(_.result)
      msg1 <- ws.receive
      msg2 <- ws.receive
      msg3 <- ws.receive
    } yield List(msg1, msg2, msg3)

    runtime.unsafeRun(test) shouldBe frames.map(Right(_))
  }

  it should "return initial responses on 'receive'" in {
    val okFrame = WebSocketFrame.text("abc")
    val exception = new Exception("boom")
    val webSocketStub = WebSocketStub.withInitialResponses(List(Success(Right(okFrame)), Failure(exception)))
    implicit val b = mkBackend(webSocketStub)
    val test = for {
      handler <- ZioWebSocketHandler()
      ws <- basicRequest.get(uri"http://example.org/a/b/c").openWebsocket(handler).map(_.result)
      msg <- ws.receive
      err <- ws.receive.either
    } yield (msg, err)

    runtime.unsafeRun(test) shouldBe ((Right(okFrame), Left(exception)))
  }

  it should "add next Incoming frames as reaction to send" in {
    val firstFrame = WebSocketFrame.text("No. 1")
    val secondFrame = WebSocketFrame.text("Second")
    val thirdFrame = WebSocketFrame.text("3")
    val expectedFrame = WebSocketFrame.text("give me more!")
    val webSocketStub = WebSocketStub
      .withInitialIncoming(List(firstFrame))
      .thenRespond {
        case `expectedFrame` => List(secondFrame, thirdFrame)
        case _               => List.empty
      }
    implicit val b = mkBackend(webSocketStub)
    val test = for {
      handler <- ZioWebSocketHandler()
      ws <- basicRequest.get(uri"http://example.org/a/b/c").openWebsocket(handler).map(_.result)
      msg1 <- ws.receive
      err1 <- ws.receive.either
      _ <- ws.send(WebSocketFrame.text("not expected"))
      err2 <- ws.receive.either
      _ <- ws.send(expectedFrame)
      msg2 <- ws.receive
      msg3 <- ws.receive
    } yield (msg1, err1.isLeft, err2.isLeft, msg2, msg3)

    runtime.unsafeRun(test) shouldBe ((Right(firstFrame), true, true, Right(secondFrame), Right(thirdFrame)))
  }

  it should "add next responses as reaction to send" in {
    val ok = WebSocketFrame.text("ok")
    val exception = new Exception("some error")

    val webSocketStub = WebSocketStub.withNoInitialResponses
      .thenRespondWith {
        case _ => List(Success(Right(ok)), Failure(exception))
      }
    implicit val b = mkBackend(webSocketStub)
    val test = for {
      handler <- ZioWebSocketHandler()
      ws <- basicRequest.get(uri"http://example.org/a/b/c").openWebsocket(handler).map(_.result)
      _ <- ws.send(WebSocketFrame.text("let's add responses"))
      msg <- ws.receive
      err1 <- ws.receive.either
      err2 <- ws.receive.either
    } yield (msg, err1, err2.isLeft)

    runtime.unsafeRun(test) shouldBe ((Right(ok), Left(exception), true))
  }

  it should "be closed after sending Close event" in {
    val ok = WebSocketFrame.text("ok")
    val closeEvent = WebSocketEvent.Close(500, "internal error")
    val webSocketStub = WebSocketStub.withInitialResponses(List(Success(Left(closeEvent)), Success(Right(ok))))

    implicit val b = mkBackend(webSocketStub)
    val test = for {
      handler <- ZioWebSocketHandler()
      ws <- basicRequest.get(uri"http://example.org/a/b/c").openWebsocket(handler).map(_.result)
      _ <- ws.send(WebSocketFrame.text("let's add responses"))
      close <- ws.receive
      isOpen <- ws.isOpen
      err <- ws.receive.either
    } yield (close, isOpen, err.isLeft)

    runtime.unsafeRun(test) shouldBe ((Left(closeEvent), false, true))
  }

  it should "use state to add next responses" in {
    val webSocketStub = WebSocketStub.withNoInitialResponses
      .thenRespondWithS(0) {
        case (counter, _) => (counter + 1, List(Success(Right(WebSocketFrame.text(s"No. $counter")))))
      }
    implicit val b = mkBackend(webSocketStub)
    val test = for {
      handler <- ZioWebSocketHandler()
      ws <- basicRequest.get(uri"http://example.org/a/b/c").openWebsocket(handler).map(_.result)
      _ <- ws.send(WebSocketFrame.text("a"))
      _ <- ws.send(WebSocketFrame.text("b"))
      _ <- ws.send(WebSocketFrame.text("c"))
      msg1 <- ws.receive
      msg2 <- ws.receive
      msg3 <- ws.receive
    } yield List(msg1, msg2, msg3)

    runtime.unsafeRun(test) shouldBe List("No. 0", "No. 1", "No. 2").map(s => Right(WebSocketFrame.text(s)))
  }
}
