package com.softwaremill.sttp.asynchttpclient.zio

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.zio.convertZioIoToFuture
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}
import zio.IO

class AsyncHttpClientZioHttpTest extends HttpTest[IO[Throwable, ?]] {

  override implicit val backend: SttpBackend[IO[Throwable, ?], Nothing] = AsyncHttpClientZioBackend()
  override implicit val convertToFuture: ConvertToFuture[IO[Throwable, ?]] = convertZioIoToFuture
}
