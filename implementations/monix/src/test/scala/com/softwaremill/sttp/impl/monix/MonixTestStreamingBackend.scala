package com.softwaremill.sttp.impl.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.testing.streaming.{ConvertToFuture, TestStreamingBackend}
import monix.eval.Task
import monix.reactive.Observable

trait MonixTestStreamingBackend extends TestStreamingBackend[Task, Observable[ByteBuffer]] {

  override implicit def convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def bodyProducer(body: String): Observable[ByteBuffer] =
    Observable.fromIterable(body.getBytes("utf-8").map(b => ByteBuffer.wrap(Array(b))))

  override def bodyConsumer(stream: Observable[ByteBuffer]): Task[String] =
    stream
      .flatMap(bb => Observable.fromIterable(bb.array()))
      .toListL
      .map(bs => new String(bs.toArray, "utf8"))

}
