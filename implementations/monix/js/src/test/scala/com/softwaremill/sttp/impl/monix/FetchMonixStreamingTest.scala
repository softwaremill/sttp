package com.softwaremill.sttp.impl.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpBackend
import monix.eval.Task
import monix.reactive.Observable

class FetchMonixStreamingTest extends MonixStreamingTest {

  override protected def endpoint: String = "localhost:51823"

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] = FetchMonixBackend()

}
