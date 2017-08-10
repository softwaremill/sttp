package com.softwaremill.sttp.model

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Path

import com.softwaremill.sttp._

sealed trait RequestBody[+S]
case object NoBody extends RequestBody[Nothing]

sealed trait BasicRequestBody extends RequestBody[Nothing] {
  def defaultContentType: Option[String]
}

case class StringBody(
    s: String,
    encoding: String,
    defaultContentType: Option[String] = Some(TextPlainContentType)
) extends BasicRequestBody

case class ByteArrayBody(
    b: Array[Byte],
    defaultContentType: Option[String] = Some(ApplicationOctetStreamContentType)
) extends BasicRequestBody

case class ByteBufferBody(
    b: ByteBuffer,
    defaultContentType: Option[String] = Some(ApplicationOctetStreamContentType)
) extends BasicRequestBody

case class InputStreamBody(
    b: InputStream,
    defaultContentType: Option[String] = Some(ApplicationOctetStreamContentType)
) extends BasicRequestBody

case class PathBody(
    f: Path,
    defaultContentType: Option[String] = Some(ApplicationOctetStreamContentType)
) extends BasicRequestBody

case class StreamBody[S](s: S) extends RequestBody[S]
