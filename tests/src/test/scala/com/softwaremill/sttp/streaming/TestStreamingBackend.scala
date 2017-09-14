package com.softwaremill.sttp.streaming

import com.softwaremill.sttp.{ForceWrappedValue, SttpBackend}

import scala.language.higherKinds

trait TestStreamingBackend[R[_], S] {
  implicit def backend: SttpBackend[R, S]

  implicit def forceResponse: ForceWrappedValue[R]

  def bodyProducer(body: String): S

  def bodyConsumer(stream: S): R[String]
}
