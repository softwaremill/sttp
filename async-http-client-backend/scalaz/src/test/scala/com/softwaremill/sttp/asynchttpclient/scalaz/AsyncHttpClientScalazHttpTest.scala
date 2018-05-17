package com.softwaremill.sttp.asynchttpclient.scalaz

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

import scalaz.concurrent.Task

class AsyncHttpClientScalazHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, Nothing] =
    AsyncHttpClientScalazBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] =
    com.softwaremill.sttp.impl.scalaz.convertToFuture
}
