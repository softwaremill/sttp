package sttp.client.asynchttpclient.fs2

import cats.effect.concurrent.Ref
import cats.effect.IO
import cats.implicits._
import fs2._
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.impl.cats.CatsTestBase
import sttp.client.impl.fs2.Fs2WebSockets
import sttp.client.testing.ToFutureWrapper
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame
import sttp.client.testing.HttpTest.wsEndpoint

import scala.collection.immutable.Queue
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class AsyncHttpClientPipedFs2WebsocketsTest
    extends AsyncFlatSpec
    with Matchers
    with ToFutureWrapper
    with CatsTestBase {
  implicit val backend: SttpBackend[IO, Nothing, WebSocketHandler] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()

  def createHandler: Option[Int] => IO[WebSocketHandler[WebSocket[IO]]] = Fs2WebSocketHandler[IO](_)

  it should "run a simple echo pipe" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocketF(createHandler(None))
      .product(Ref.of[IO, Queue[String]](Queue.empty))
      .flatMap {
        case (response, results) =>
          Fs2WebSockets.handleSocketThroughTextPipe(response.result) { in =>
            val receive = in.evalMap(m => results.update(_.enqueue(m)))
            val send = Stream("Message 1".asRight, "Message 2".asRight, WebSocketFrame.close.asLeft)
            send merge receive.drain
          } >> results.get.map(_ should contain theSameElementsInOrderAs List("echo: Message 1", "echo: Message 2"))
      }
      .toFuture()
  }

  it should "run a simple read-only client" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocketF(createHandler(None))
      .product(Ref.of[IO, Queue[String]](Queue.empty))
      .flatMap {
        case (response, results) =>
          Fs2WebSockets.handleSocketThroughTextPipe(response.result) { in =>
            in.evalMap(m => results.update(_.enqueue(m))).drain
          } >> results.get.map(_ should contain theSameElementsInOrderAs List("test10", "test20"))
      }
      .toFuture()
  }
}
