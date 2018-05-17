package com.softwaremill.sttp.impl.monix

import java.nio.ByteBuffer

import _root_.monix.eval.Task
import _root_.monix.reactive.Observable
import com.softwaremill.sttp.testing.streaming.{ConvertToFuture, TestStreamingBackend}

trait MonixTestStreamingBackend extends TestStreamingBackend[Task, Observable[ByteBuffer]] {

  override implicit def convertToFuture: ConvertToFuture[Task] = com.softwaremill.sttp.impl.monix.convertToFuture

  override def bodyProducer(body: String): Observable[ByteBuffer] =
    Observable.fromIterable(body.getBytes("utf-8").map(b => ByteBuffer.wrap(Array(b))))

  override def bodyConsumer(stream: Observable[ByteBuffer]): Task[String] =
    stream
      .flatMap(bb => Observable.fromIterable(bb.array()))
      .toListL
      .map(bs => new String(bs.toArray, "utf8"))

}
