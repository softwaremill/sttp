package com.softwaremill.sttp.asynchttpclient.ziostreams

import java.nio.ByteBuffer
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.zio.convertZioIoToFuture
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}
import scalaz.zio._
import scalaz.zio.stream._

class AsyncHttpClientZioStreamsHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioStreamsBackend(new DefaultRuntime {})
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture
}
