package com.softwaremill.sttp.asynchttpclient.zio

import java.nio.ByteBuffer
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.zio.convertZioIoToFuture
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}
import scalaz.zio._
import scalaz.zio.stream._

class AsyncHttpClientZioHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(new DefaultRuntime {})
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture
}
