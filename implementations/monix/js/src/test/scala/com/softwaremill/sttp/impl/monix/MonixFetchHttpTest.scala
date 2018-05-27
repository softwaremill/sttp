package com.softwaremill.sttp.impl.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.AbstractFetchHttpTest
import com.softwaremill.sttp.testing.ConvertToFuture
import monix.eval.Task
import monix.reactive.Observable

class MonixFetchHttpTest extends AbstractFetchHttpTest[Task, Observable[ByteBuffer]] {

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] = FetchMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
}
