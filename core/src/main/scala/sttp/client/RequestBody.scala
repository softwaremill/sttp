package sttp.client

import java.io.InputStream
import java.nio.ByteBuffer

import sttp.capabilities.Streams
import sttp.model._
import sttp.client.internal.SttpFile
import sttp.model.internal.UriCompatibility

import scala.collection.immutable.Seq

sealed trait RequestBody[-R]
case object NoBody extends RequestBody[Any]

sealed trait BasicRequestBody extends RequestBody[Any] {
  def defaultContentType: Option[MediaType]
}

case class StringBody(
    s: String,
    encoding: String,
    defaultContentType: Option[MediaType] = Some(MediaType.TextPlain)
) extends BasicRequestBody

case class ByteArrayBody(
    b: Array[Byte],
    defaultContentType: Option[MediaType] = Some(MediaType.ApplicationOctetStream)
) extends BasicRequestBody

case class ByteBufferBody(
    b: ByteBuffer,
    defaultContentType: Option[MediaType] = Some(MediaType.ApplicationOctetStream)
) extends BasicRequestBody

case class InputStreamBody(
    b: InputStream,
    defaultContentType: Option[MediaType] = Some(MediaType.ApplicationOctetStream)
) extends BasicRequestBody

case class FileBody(
    f: SttpFile,
    defaultContentType: Option[MediaType] = Some(MediaType.ApplicationOctetStream)
) extends BasicRequestBody

// Path-dependent types are not supported in constructor arguments or the extends clause. Thus we cannot express the
// fact that `BinaryStream =:= s.BinaryStream`. We have to rely on correct construction via the companion object and
// perform typecasts when the request is deconstructed.
case class StreamBody[BinaryStream, S] private (b: BinaryStream) extends RequestBody[S]
object StreamBody {
  def apply[S](s: Streams[S])(b: s.BinaryStream): StreamBody[s.BinaryStream, S] = new StreamBody(b)
}

case class MultipartBody(parts: Seq[Part[BasicRequestBody]]) extends RequestBody[Any]

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
