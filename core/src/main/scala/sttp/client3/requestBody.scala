package sttp.client3

import java.io.InputStream
import java.nio.ByteBuffer

import sttp.capabilities.Streams
import sttp.client3.internal.SttpFile
import sttp.model._
import sttp.model.internal.UriCompatibility

import scala.collection.immutable.Seq

sealed trait GenericRequestBody[-R] {
  def defaultContentType: MediaType
  def show: String
}

sealed trait BasicBody extends GenericRequestBody[Any]

case object NoBody extends BasicBody {
  override def defaultContentType: MediaType = MediaType.ApplicationOctetStream
  def show: String = "empty"
}

sealed trait BodyPart[-S] extends GenericRequestBody[S]

sealed trait BasicBodyPart extends BasicBody with BodyPart[Any]

case class StringBody(
    s: String,
    encoding: String,
    defaultContentType: MediaType = MediaType.TextPlain
) extends BasicBodyPart {
  override def show: String = s"string: $s"
}

case class ByteArrayBody(
    b: Array[Byte],
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicBodyPart {
  override def show: String = "byte array"
}

case class ByteBufferBody(
    b: ByteBuffer,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicBodyPart {
  override def show: String = "byte buffer"
}

case class InputStreamBody(
    b: InputStream,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicBodyPart {
  override def show: String = "input stream"
}

// Path-dependent types are not supported in constructor arguments or the extends clause. Thus we cannot express the
// fact that `BinaryStream =:= s.BinaryStream`. We have to rely on correct construction via the companion object and
// perform typecasts when the request is deconstructed.
case class StreamBody[BinaryStream, S] private (b: BinaryStream) extends BodyPart[S] {
  override def defaultContentType: MediaType = MediaType.ApplicationOctetStream
  override def show: String = "stream"
}
object StreamBody {
  def apply[S](s: Streams[S])(b: s.BinaryStream): StreamBody[s.BinaryStream, S] = new StreamBody(b)
}

case class FileBody(
    f: SttpFile,
    defaultContentType: MediaType = MediaType.ApplicationOctetStream
) extends BasicBodyPart {
  override def show: String = s"file: ${f.name}"
}

sealed trait MultipartBody[S] extends GenericRequestBody[S] {
  override def defaultContentType: MediaType = MediaType.MultipartFormData
  override def show: String = s"multipart: ${parts.map(p => p.name).mkString(",")}"
  def parts: Seq[Part[BodyPart[S]]]
}

case class MultipartStreamBody[S](parts: Seq[Part[BodyPart[S]]]) extends MultipartBody[S]

case class BasicMultipartBody(parts: Seq[Part[BasicBodyPart]]) extends MultipartBody[Any] with BasicBody

object BasicBody {
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
