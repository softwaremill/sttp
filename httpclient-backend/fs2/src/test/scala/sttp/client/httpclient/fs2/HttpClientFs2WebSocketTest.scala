package sttp.client.httpclient.fs2

import cats.effect.IO
import sttp.client.impl.fs2.Fs2Streams
import sttp.client.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.ws.WebSocketFrame

class HttpClientFs2WebSocketTest
    extends WebSocketTest[IO]
    with WebSocketStreamingTest[IO, Fs2Streams[IO]]
    with HttpClientFs2TestBase {
  override val streams: Fs2Streams[IO] = new Fs2Streams[IO] {}

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): fs2.Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] = _.map(f)
}
