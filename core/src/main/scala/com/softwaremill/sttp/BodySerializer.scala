package com.softwaremill.sttp

import com.softwaremill.sttp.model.BasicRequestBody

/**
  * Provide an implicit value of this type to serialize arbitrary classes into a request body.
  * Handlers might also provide special logic for serializer instances which they define (e.g. to handle streaming).
  */
trait BodySerializer[B] {
  def apply(body: B): BasicRequestBody
  def defaultContentType: Option[String]
}

object BodySerializer {
  final def apply[B](implicit instance: BodySerializer[B]): BodySerializer[B] =
    instance

  final def instance[B](f: B => BasicRequestBody) =
    new BodySerializer[B] {
      def apply(body: B): BasicRequestBody = f(body)
      val defaultContentType: Option[String] = None
    }

  final def instance[B](f: B => BasicRequestBody, contentType: String) =
    new BodySerializer[B] {
      def apply(body: B): BasicRequestBody = f(body)
      val defaultContentType: Option[String] = Option(contentType)
    }
}
