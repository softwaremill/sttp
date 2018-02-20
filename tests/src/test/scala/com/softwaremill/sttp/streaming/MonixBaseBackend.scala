package com.softwaremill.sttp.streaming

import java.nio.ByteBuffer

import com.softwaremill.sttp.ForceWrappedValue
import monix.eval.Task
import monix.reactive.Observable

trait MonixBaseBackend extends TestStreamingBackend[Task, Observable[ByteBuffer]] {

  override implicit def forceResponse: ForceWrappedValue[Task] =
    ForceWrappedValue.monixTask

  override def bodyProducer(body: String): Observable[ByteBuffer] =
    Observable.fromIterable(body.getBytes("utf-8").map(b => ByteBuffer.wrap(Array(b))))

  override def bodyConsumer(stream: Observable[ByteBuffer]): Task[String] =
    stream
      .flatMap(bb => Observable.fromIterable(bb.array()))
      .toListL
      .map(bs => new String(bs.toArray, "utf8"))

}
