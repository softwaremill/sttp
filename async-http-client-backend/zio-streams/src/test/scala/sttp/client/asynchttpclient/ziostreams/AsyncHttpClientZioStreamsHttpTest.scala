package sttp.client.asynchttpclient.ziostreams

import java.nio.ByteBuffer

import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.zio._
import sttp.client.testing.{ConvertToFuture, HttpTest}
import zio._
import zio.stream._

class AsyncHttpClientZioStreamsHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, Stream[Throwable, ByteBuffer], NothingT] =
    runtime.unsafeRunSync(AsyncHttpClientZioStreamsBackend(runtime)).getOrElse(c => throw c.squash)
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture
}
