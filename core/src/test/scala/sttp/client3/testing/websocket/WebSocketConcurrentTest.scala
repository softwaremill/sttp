package sttp.client3.testing.websocket

import org.scalatest.Suite
import org.scalatest.flatspec.AsyncFlatSpecLike
import sttp.capabilities.WebSockets
import sttp.client3._
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.HttpTest.wsEndpoint
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.WebSocket

trait WebSocketConcurrentTest[F[_]] { outer: Suite with AsyncFlatSpecLike with WebSocketTest[F] =>
  val backend: SttpBackend[F, WebSockets]
  implicit def monad: MonadError[F]
  implicit val convertToFuture: ConvertToFuture[F]

  it should "send & receive messages concurrently" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { (ws: WebSocket[F]) =>
        val n = 32
        val tasks = List.fill(n)(() =>
          for {
            _ <- ws.sendText("test")
            t <- ws.receiveText()
          } yield t
        )

        for {
          r <- concurrently(tasks)
          _ <- ws.close()
        } yield {
          r shouldBe List.fill(n)("echo: test")
        }
      })
      .send(backend)
      .map(_ => succeed)
      .toFuture()
  }

  def concurrently[T](fs: List[() => F[T]]): F[List[T]]
}
