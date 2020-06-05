package sttp.client.asynchttpclient.zio

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client._
import sttp.client.testing.SttpBackendStub
import sttp.client.impl.zio._
import sttp.client.monad.MonadError
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.Headers
import sttp.model.ws._
import zio._

class SttpBackendStubZioTests extends AnyFlatSpec with Matchers with ScalaFutures {

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    implicit val b: SttpBackendStub[Task, Nothing, NothingT] = SttpBackendStub(new RIOMonadAsyncError[Any])
      .whenRequestMatches(_ => true)
      .thenRespondCyclic("a", "b", "c")

    // when
    val r = basicRequest.get(uri"http://example.org/a/b/c").send()

    // then
    runtime.unsafeRun(r).body shouldBe Right("a")
    runtime.unsafeRun(r).body shouldBe Right("b")
    runtime.unsafeRun(r).body shouldBe Right("c")
    runtime.unsafeRun(r).body shouldBe Right("a")
  }

  it should "return given web socket response" in {
    val rioMonad: MonadError[zio.Task] = new RIOMonadAsyncError[Any]
    val frame1 = WebSocketFrame.text("initial frame")
    val sentFrame = WebSocketFrame.text("sent frame")

    def webSocket(queue: Queue[WebSocketFrame.Incoming]) =
      new WebSocket[Task] {
        override def isOpen: zio.Task[Boolean] = Task.succeed(true)
        override def monad: MonadError[zio.Task] = rioMonad
        override def receive: zio.Task[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]] = queue.take.map(Right(_))
        override def send(f: WebSocketFrame, isContinuation: Boolean): zio.Task[Unit] =
          f match {
            case t: WebSocketFrame.Text => queue.offer(t).unit
            case _                      => Task.unit
          }
      }

    def makeBackend(queue: Queue[WebSocketFrame.Incoming]) =
      AsyncHttpClientZioBackend.stub
        .whenRequestMatches(_ => true)
        .thenRespondWebSocket(Headers(List.empty), webSocket(queue))

    val test = for {
      queue <- Queue.unbounded[WebSocketFrame.Incoming]
      _ <- queue.offer(frame1)
      backend = makeBackend(queue)
      handler <- ZioWebSocketHandler()
      request = basicRequest.get(uri"http://example.org/a/b/c")
      ws <- backend.openWebsocket(request, handler).map(_.result)
      msg1 <- ws.receive
      _ <- ws.send(sentFrame, false)
      msg2 <- ws.receive
    } yield (msg1, msg2)

    runtime.unsafeRun(test) shouldBe ((Right(frame1), Right(sentFrame)))
  }
}
