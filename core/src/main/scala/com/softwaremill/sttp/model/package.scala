package com.softwaremill.sttp

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.file.Path

import scala.language.higherKinds

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
    val CONNECT = Method("CONNECT")
    val TRACE = Method("TRACE")
  }

  /**
    * Provide an implicit value of this type to serialize arbitrary classes into a request body.
    * Handlers might also provide special logic for serializer instances which they define (e.g. to handle streaming).
    */
  type BodySerializer[T] = T => BasicRequestBody

  sealed trait RequestBody
  case object NoBody extends RequestBody
  case class SerializableBody[T](f: BodySerializer[T], t: T) extends RequestBody

  sealed trait BasicRequestBody extends RequestBody
  case class StringBody(s: String, encoding: String) extends BasicRequestBody
  case class ByteArrayBody(b: Array[Byte]) extends BasicRequestBody
  case class ByteBufferBody(b: ByteBuffer) extends BasicRequestBody
  case class InputStreamBody(b: InputStream) extends BasicRequestBody
  case class FileBody(f: File) extends BasicRequestBody
  case class PathBody(f: Path) extends BasicRequestBody

  /**
    * @tparam T Target type as which the response will be read.
    * @tparam S If `T` is a stream, the type of the stream. Otherwise, `Nothing`.
    */
  sealed trait ResponseAs[T, +S]

  sealed trait ResponseAsBasic[T, +S] extends ResponseAs[T, S]
  object IgnoreResponse extends ResponseAsBasic[Unit, Nothing]
  case class ResponseAsString(encoding: String) extends ResponseAsBasic[String, Nothing]
  object ResponseAsByteArray extends ResponseAsBasic[Array[Byte], Nothing]
  // response as params

  case class ResponseAsStream[T, S]()(implicit val responseIsStream: S =:= T) extends ResponseAs[T, S]
}
