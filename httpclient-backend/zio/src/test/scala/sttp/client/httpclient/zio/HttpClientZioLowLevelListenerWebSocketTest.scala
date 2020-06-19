package sttp.client.httpclient.zio

import sttp.client.SttpBackend
import sttp.client.httpclient.{HttpClientLowLevelListenerWebSocketTest, WebSocketHandler}
import sttp.client.testing.ConvertToFuture
import sttp.client.impl.zio._
import zio.blocking.Blocking
import zio.stream.ZStream

class HttpClientZioLowLevelListenerWebSocketTest extends HttpClientLowLevelListenerWebSocketTest[BlockingTask] {
  override implicit val backend: SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], WebSocketHandler] =
    runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture
}
