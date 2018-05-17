package com.softwaremill.sttp.impl.monix

import com.softwaremill.sttp.testing.ConvertToFuture
import com.softwaremill.sttp.testing.streaming.TestStreamingBackend
import monix.eval.Task
import monix.reactive.Observable

trait MonixTestStreamingBackend[T] extends TestStreamingBackend[Task, Observable[T]] {

  def toByteArray(v: T): Array[Byte]
  def fromByteArray(v: Array[Byte]): T

  override implicit def convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

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
