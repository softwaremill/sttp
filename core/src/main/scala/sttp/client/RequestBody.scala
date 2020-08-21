package sttp.client

import java.io.InputStream
import java.nio.ByteBuffer

import sttp.capabilities.Streams
import sttp.model._
import sttp.client.internal.SttpFile
import sttp.model.internal.UriCompatibility

import scala.collection.immutable.Seq

sealed trait RequestBody[-R] {
  def defaultContentType: MediaType
}
case object NoBody extends RequestBody[Any] {
  override def defaultContentType: MediaType = MediaType.ApplicationOctetStream
}

sealed trait BasicRequestBody extends RequestBody[Any]

case class StringBody(
    s: String,
    encoding: String,
    defaultContentType: MediaType = MediaType.TextPlain
) extends BasicRequestBody

case class ByteArrayBody(
    b: Array[Byte],
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicRequestBody

case class ByteBufferBody(
    b: ByteBuffer,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicRequestBody

case class InputStreamBody(
    b: InputStream,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicRequestBody

case class FileBody(
    f: SttpFile,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicRequestBody

// Path-dependent types are not supported in constructor arguments or the extends clause. Thus we cannot express the
// fact that `BinaryStream =:= s.BinaryStream`. We have to rely on correct construction via the companion object and
// perform typecasts when the request is deconstructed.
case class StreamBody[BinaryStream, S] private (b: BinaryStream) extends RequestBody[S] {
  override def defaultContentType: MediaType = MediaType.ApplicationOctetStream
}
object StreamBody {
  def apply[S](s: Streams[S])(b: s.BinaryStream): StreamBody[s.BinaryStream, S] = new StreamBody(b)
}

case class MultipartBody[R](parts: Seq[Part[RequestBody[R]]]) extends RequestBody[R] {
  override def defaultContentType: MediaType = MediaType.MultipartFormData
}

object RequestBody {
  private[client] def paramsToStringBody(fs: Seq[(String, String)], encoding: String): StringBody = {
    val b = fs
      .map {
        case (key, value) =>
          UriCompatibility.encodeQuery(key, encoding) + "=" +
            UriCompatibility.encodeQuery(value, encoding)
      }
      .mkString("&")

    StringBody(b, encoding)
  }
}
