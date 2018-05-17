package com.softwaremill.sttp.impl.monix

import _root_.monix.eval.Task
import _root_.monix.reactive.Observable
import com.softwaremill.sttp.testing.ConvertToFuture
import com.softwaremill.sttp.testing.streaming.TestStreamingBackend

trait MonixTestStreamingBackend[T] extends TestStreamingBackend[Task, Observable[T]] {

  def toByteArray(v: T): Array[Byte]
  def fromByteArray(v: Array[Byte]): T

  override implicit def convertToFuture: ConvertToFuture[Task] =
    com.softwaremill.sttp.impl.monix.convertToFuture

  override def bodyProducer(body: String): Observable[T] =
    Observable
      .fromIterable(
        body.getBytes("utf-8")
      )
      .map(v => fromByteArray(Array(v)))

  override def bodyConsumer(stream: Observable[T]): Task[String] =
    stream
      .flatMap(v => Observable.fromIterable(toByteArray(v)))
      .toListL
      .map(bs => new String(bs.toArray, "utf8"))

}
