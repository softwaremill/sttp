package com.softwaremill.sttp.model

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Path

import com.softwaremill.sttp.BodySerializer

sealed trait RequestBody[+S]
case object NoBody extends RequestBody[Nothing]
case class SerializableBody[T](f: BodySerializer[T], t: T)
    extends RequestBody[Nothing]

sealed trait BasicRequestBody extends RequestBody[Nothing]
case class StringBody(s: String, encoding: String) extends BasicRequestBody
case class ByteArrayBody(b: Array[Byte]) extends BasicRequestBody
case class ByteBufferBody(b: ByteBuffer) extends BasicRequestBody
case class InputStreamBody(b: InputStream) extends BasicRequestBody
case class PathBody(f: Path) extends BasicRequestBody

case class StreamBody[S](s: S) extends RequestBody[S]
