package com.softwaremill.sttp.streaming

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.okhttp.monix.OkHttpMonixBackend
import monix.eval.Task
import monix.reactive.Observable

class OkHttpMonixStreamingTests extends MonixBaseBackend {

  import monix.execution.Scheduler.Implicits.global

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] =
    OkHttpMonixBackend()

}
