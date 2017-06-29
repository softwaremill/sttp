package com.softwaremill.sttp

import java.io.InputStream

import akka.stream.scaladsl.Source
import akka.util.ByteString

package object akkahttp {
  implicit object AkkaStreamsSourceResponseBody extends ResponseBodyReader[Source[ByteString, Any]] {
    override def fromInputStream(is: InputStream): Source[ByteString, Any] = ???

    override def fromBytes(bytes: Array[Byte]): Source[ByteString, Any] = ???
  }
}
