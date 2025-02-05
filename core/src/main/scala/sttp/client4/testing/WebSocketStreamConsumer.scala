package sttp.client4.testing

import sttp.capabilities.Streams
import sttp.ws.WebSocketFrame

/** Body which can be used to provide behavior for [[sttp.client4.ws.stream.asWebSocketStream]] response descriptions,
  * when creating a [[BackendStub]].
  */
case class WebSocketStreamConsumer[S, Pipe[_, _], F[_]] private (
    consume: Pipe[WebSocketFrame.Data[_], WebSocketFrame] => F[Unit]
)

object WebSocketStreamConsumer {
  def apply[F[_]]: WebSocketStreamConsumerCreator[F] = new WebSocketStreamConsumerCreator[F] {}

  trait WebSocketStreamConsumerCreator[F[_]] {
    def apply[S <: Streams[S]](s: Streams[S])(
        consume: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame] => F[Unit]
    ): WebSocketStreamConsumer[S, s.Pipe, F] = new WebSocketStreamConsumer(consume)
  }
}
