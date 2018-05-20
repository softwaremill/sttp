package com.softwaremill.sttp.asynchttpclient.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.monix.MonixStreamingTest
import monix.eval.Task
import monix.reactive.Observable

class AsyncHttpClientMonixStreamingTest extends MonixStreamingTest {

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend()
}
