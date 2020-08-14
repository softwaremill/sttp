package sttp.client.httpclient.zio

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.BlockingZioStreams
import sttp.client._
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.impl.zio.ZioTestBase
import sttp.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.ws.WebSocketFrame
import zio.stream.Transducer

class HttpClientZioWebSocketTest
    extends WebSocketTest[BlockingTask]
    with WebSocketStreamingTest[BlockingTask, BlockingZioStreams]
    with ZioTestBase {
  implicit val backend: SttpBackend[BlockingTask, BlockingZioStreams with WebSockets] =
    runtime.unsafeRun(HttpClientZioBackend())
  implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture
  implicit val monad: MonadError[BlockingTask] = new RIOMonadAsyncError
  override val streams: BlockingZioStreams = BlockingZioStreams

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): Transducer[Throwable, WebSocketFrame.Data[_], WebSocketFrame] =
    Transducer.identity.map(f)
}
