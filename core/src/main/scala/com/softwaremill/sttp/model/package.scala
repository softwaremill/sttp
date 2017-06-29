package com.softwaremill.sttp

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.file.Path

package object model {
  case class Method(m: String) extends AnyVal
  object Method {
    val GET = Method("GET")
    val HEAD = Method("HEAD")
    val POST = Method("POST")
    val PUT = Method("PUT")
    val DELETE = Method("DELETE")
    val OPTIONS = Method("OPTIONS")
    val PATCH = Method("PATCH")
  }

  sealed trait RequestBody
  sealed trait BasicRequestBody extends RequestBody
  case object NoBody extends RequestBody
  case class StringBody(s: String) extends BasicRequestBody
  case class ByteArrayBody(b: Array[Byte]) extends BasicRequestBody
  case class ByteBufferBody(b: ByteBuffer) extends BasicRequestBody
  case class InputStreamBody(b: InputStream) extends BasicRequestBody
  case class InputStreamSupplierBody(b: () => InputStream) extends BasicRequestBody
  case class FileBody(f: File) extends BasicRequestBody
  case class PathBody(f: Path) extends BasicRequestBody

  sealed trait ResponseAs[T]
  object IgnoreResponse extends ResponseAs[Unit]
  case class ResponseAsString(encoding: String) extends ResponseAs[String]
  object ResponseAsByteArray extends ResponseAs[Array[Byte]]

  case class ResponseAsStream[-S]()
}
