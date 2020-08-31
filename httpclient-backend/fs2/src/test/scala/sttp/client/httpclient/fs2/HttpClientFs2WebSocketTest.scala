package sttp.client.httpclient.fs2

import cats.effect.IO
import cats.implicits._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.ws.WebSocketFrame

class HttpClientFs2WebSocketTest
    extends WebSocketTest[IO]
    with WebSocketStreamingTest[IO, Fs2Streams[IO]]
    with HttpClientFs2TestBase {
  override val streams: Fs2Streams[IO] = new Fs2Streams[IO] {}

  override def functionToPipe(
      initial: List[WebSocketFrame.Data[_]],
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): fs2.Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] = in => fs2.Stream.emits(initial) ++ in.mapFilter(f)
}
