package com.softwaremill.sttp.asynchttpclient.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.monix.MonixTestStreamingBackend
import com.softwaremill.sttp.testing.streaming.{StreamingTest, TestStreamingBackend}
import _root_.monix.eval.Task
import _root_.monix.reactive.Observable

class AsyncHttpClientMonixStreamingTest extends StreamingTest[Task, Observable[ByteBuffer]] {

  override val testStreamingBackend: TestStreamingBackend[Task, Observable[ByteBuffer]] =
    new AsyncHttpClientMonixTestStreamingBackend
}

class AsyncHttpClientMonixTestStreamingBackend extends MonixTestStreamingBackend[ByteBuffer] {

  import _root_.monix.execution.Scheduler.Implicits.global

  override def toByteArray(v: ByteBuffer): Array[Byte] = v.array()
  override def fromByteArray(v: Array[Byte]): ByteBuffer = ByteBuffer.wrap(v)

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend()
}
