package com.softwaremill.sttp.streaming

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.softwaremill.sttp.impl.monix.MonixTestStreamingBackend
import monix.eval.Task
import monix.reactive.Observable

class AsyncHttpClientMonixStreamingTests extends MonixTestStreamingBackend {

  import monix.execution.Scheduler.Implicits.global

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend()

}
