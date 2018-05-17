package com.softwaremill.sttp.testing.streaming

import com.softwaremill.sttp.SttpBackend

import scala.language.higherKinds

trait TestStreamingBackend[R[_], S] {
  implicit def backend: SttpBackend[R, S]

  implicit def convertToFuture: ConvertToFuture[R]

  def bodyProducer(body: String): S

  def bodyConsumer(stream: S): R[String]
}
