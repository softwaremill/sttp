package com.softwaremill.sttp.impl.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.testing.ConvertToFuture
import com.softwaremill.sttp.testing.streaming.StreamingTest
import monix.eval.Task
import monix.reactive.Observable

abstract class MonixStreamingTest extends StreamingTest[Task, Observable[ByteBuffer]] {

  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def bodyProducer(body: String): Observable[ByteBuffer] =
    Observable
      .fromIterable(
        body.getBytes("utf-8")
      )
      .map(v => ByteBuffer.wrap(Array(v)))

  override def bodyConsumer(stream: Observable[ByteBuffer]): Task[String] =
    stream
      .flatMap(v => Observable.fromIterable(v.array()))
      .toListL
      .map(bs => new String(bs.toArray, "utf8"))
}
