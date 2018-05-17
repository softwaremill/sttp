package com.softwaremill.sttp.okhttp.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.monix.MonixTestStreamingBackend
import com.softwaremill.sttp.testing.streaming.{StreamingTest, TestStreamingBackend}
import monix.eval.Task
import monix.reactive.Observable

class OkHttpMonixStreamingTest extends StreamingTest[Task, Observable[ByteBuffer]] {

  override val testStreamingBackend: TestStreamingBackend[Task, Observable[ByteBuffer]] =
    new OkHttpMonixTestStreamingBackend
}

class OkHttpMonixTestStreamingBackend extends MonixTestStreamingBackend[ByteBuffer] {

  import monix.execution.Scheduler.Implicits.global

  override def toByteArray(v: ByteBuffer): Array[Byte] = v.array()
  override def fromByteArray(v: Array[Byte]): ByteBuffer = ByteBuffer.wrap(v)

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] =
    OkHttpMonixBackend()
}
