package sttp.client.akkahttp

import akka.stream.scaladsl.Flow
import sttp.client._
import sttp.client.monad.{FutureMonad, MonadError}
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.WebSocketTest
import sttp.model.ws.WebSocketFrame

import scala.concurrent.{ExecutionContext, Future}

class AkkaHttpWebsocketTest extends WebSocketTest[Future, AkkaStreams] {
  override val streams: AkkaStreams = AkkaStreams
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override implicit val backend: SttpBackend[Future, AkkaStreams with WebSockets] = AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad

  override def functionToPipe(
      f: WebSocketFrame.Incoming => WebSocketFrame
  ): Flow[WebSocketFrame.Incoming, WebSocketFrame, Any] =
    Flow.fromFunction(f)
}
