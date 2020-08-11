package sttp.client.testing.websocket

import org.scalatest.Suite
import org.scalatest.flatspec.AsyncFlatSpecLike
import sttp.client.monad.MonadError
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.{ConvertToFuture, ToFutureWrapper}
import sttp.client.{Streams, SttpBackend, WebSockets, asWebSocketStreamAlways, basicRequest}
import sttp.model.ws.WebSocketFrame
import sttp.client._
import sttp.client.monad.syntax._

trait WebSocketStreamingTest[F[_], S] extends ToFutureWrapper { outer: Suite with AsyncFlatSpecLike =>
  val streams: Streams[S]
  implicit val backend: SttpBackend[F, S with WebSockets]
  implicit val monad: MonadError[F]
  implicit val convertToFuture: ConvertToFuture[F]

  it should "use pipe to process websocket messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_expect_echo")
      .response(asWebSocketStreamAlways(streams)(functionToPipe {
        case WebSocketFrame.Text(payload, _, _) =>
          WebSocketFrame.text(payload + "-echo")
      }))
      .send()
      .map(_ => succeed)
      .toFuture()
  }

  def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
}
