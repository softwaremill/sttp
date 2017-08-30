package com.softwaremill.sttp.streaming

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpHandler
import com.softwaremill.sttp.okhttp.monix.OkHttpMonixHandler
import monix.eval.Task
import monix.reactive.Observable

class OkHttpMonixStreamingTests extends MonixBaseHandler {

  import monix.execution.Scheduler.Implicits.global

  override implicit val handler: SttpHandler[Task, Observable[ByteBuffer]] =
    OkHttpMonixHandler()

}
