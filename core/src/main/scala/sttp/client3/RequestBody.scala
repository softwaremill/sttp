package sttp.client3

import java.io.InputStream
import java.nio.ByteBuffer

import sttp.capabilities.Streams
import sttp.model._
import sttp.client3.internal.SttpFile
import sttp.model.internal.UriCompatibility

import scala.collection.immutable.Seq

sealed trait RequestBody[-R] {
  def defaultContentType: MediaType
  def show: String
}
case object NoBody extends RequestBody[Any] {
  override def defaultContentType: MediaType = MediaType.ApplicationOctetStream
  def show: String = "empty"
}

sealed trait BasicRequestBody extends RequestBody[Any]

case class StringBody(
    s: String,
    encoding: String,
    defaultContentType: MediaType = MediaType.TextPlain
) extends BasicRequestBody {
  override def show: String = s"string: $s"
}

case class ByteArrayBody(
    b: Array[Byte],
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicRequestBody {
  override def show: String = "byte array"
}

case class ByteBufferBody(
    b: ByteBuffer,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicRequestBody {
  override def show: String = "byte buffer"
}

case class InputStreamBody(
    b: InputStream,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicRequestBody {
  override def show: String = "input stream"
}

case class FileBody(
    f: SttpFile,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicRequestBody {
  override def show: String = s"file: ${f.name}"
}

// Path-dependent types are not supported in constructor arguments or the extends clause. Thus we cannot express the
// fact that `BinaryStream =:= s.BinaryStream`. We have to rely on correct construction via the companion object and
// perform typecasts when the request is deconstructed.
case class StreamBody[BinaryStream, S] private (b: BinaryStream) extends RequestBody[S] {
  override def defaultContentType: MediaType = MediaType.ApplicationOctetStream
  override def show: String = "stream"
}
object StreamBody {
  def apply[S](s: Streams[S])(b: s.BinaryStream): StreamBody[s.BinaryStream, S] = new StreamBody(b)
}

case class MultipartBody[R](parts: Seq[Part[RequestBody[R]]]) extends RequestBody[R] {
  override def defaultContentType: MediaType = MediaType.MultipartFormData
  override def show: String = s"multipart: ${parts.map(p => p.name).mkString(",")}"
}

object RequestBody {
  private[client3] def paramsToStringBody(fs: Seq[(String, String)], encoding: String): StringBody = {
    val b = fs
      .map { case (key, value) =>
        UriCompatibility.encodeQuery(key, encoding) + "=" +
          UriCompatibility.encodeQuery(value, encoding)
      }
      .mkString("&")

    StringBody(b, encoding)
  }
}
