package com.softwaremill.sttp.asynchttpclient.zio

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.zio._
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}
import zio.Task

class AsyncHttpClientZioHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, Nothing] =
    runtime.unsafeRunSync(AsyncHttpClientZioBackend()).getOrElse(c => throw c.squash)
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture
}
