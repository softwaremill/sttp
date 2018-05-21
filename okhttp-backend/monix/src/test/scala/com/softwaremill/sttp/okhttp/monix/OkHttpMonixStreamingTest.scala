package com.softwaremill.sttp.okhttp.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.monix.MonixStreamingTest
import monix.eval.Task
import monix.reactive.Observable

class OkHttpMonixStreamingTest extends MonixStreamingTest {

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] = OkHttpMonixBackend()
}
