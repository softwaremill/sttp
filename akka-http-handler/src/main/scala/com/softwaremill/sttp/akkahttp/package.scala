package com.softwaremill.sttp

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.model.{BasicRequestBody, BodySerializer}

package object akkahttp {
  private[akkahttp] case object SourceBodySerializer
      extends BodySerializer[Source[ByteString, Any]] {
    def apply(t: Source[ByteString, Any]): BasicRequestBody =
      throw new RuntimeException("Can only be used with akka-http handler")
  }

  implicit val sourceBodySerializer: BodySerializer[Source[ByteString, Any]] =
    SourceBodySerializer
}
