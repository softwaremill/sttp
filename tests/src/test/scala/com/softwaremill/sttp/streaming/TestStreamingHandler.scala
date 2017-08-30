package com.softwaremill.sttp.streaming

import com.softwaremill.sttp.{ForceWrappedValue, SttpHandler}

import scala.language.higherKinds

trait TestStreamingHandler[R[_], S] {
  implicit def handler: SttpHandler[R, S]

  implicit def forceResponse: ForceWrappedValue[R]

  def bodyProducer(body: String): S

  def bodyConsumer(stream: S): R[String]
}
