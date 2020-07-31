package sttp.client.asynchttpclient

import java.nio.charset.Charset

import io.netty.buffer.ByteBuf
import org.asynchttpclient.{Param, RequestBuilder}
import org.asynchttpclient.request.body.multipart.{ByteArrayPart, FilePart, StringPart}
import org.reactivestreams.Publisher
import sttp.client.{
  BasicRequestBody,
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  RequestBody,
  StreamBody,
  Streams,
  StringBody
}
import sttp.client.internal.toByteArray
import sttp.model.{HeaderNames, MediaType, Part}
import scala.collection.JavaConverters._

private[asynchttpclient] trait BodyToAHC[F[_], S] {
  val streams: Streams[S]
  protected def streamToPublisher(s: streams.BinaryStream): Publisher[ByteBuf]

  def apply[R](r: Request[_, R], body: RequestBody[R], rb: RequestBuilder): Unit = {
    body match {
      case NoBody => // skip

      case StringBody(b, encoding, _) =>
        rb.setBody(b.getBytes(encoding))

      case ByteArrayBody(b, _) =>
        rb.setBody(b)

      case ByteBufferBody(b, _) =>
        rb.setBody(b)

      case InputStreamBody(b, _) =>
        rb.setBody(b)

      case FileBody(b, _) =>
        rb.setBody(b.toFile)

      case StreamBody(s) =>
        val cl = r.headers
          .find(_.is(HeaderNames.ContentLength))
          .map(_.value.toLong)
          .getOrElse(-1L)
        rb.setBody(streamToPublisher(s.asInstanceOf[streams.BinaryStream]), cl)

      case MultipartBody(ps) =>
        ps.foreach(addMultipartBody(rb, _))
    }
  }

  private def addMultipartBody(rb: RequestBuilder, mp: Part[BasicRequestBody]): Unit = {
    // async http client only supports setting file names on file parts. To
    // set a file name on an arbitrary part we have to use a small "work
    // around", combining the file name with the name (surrounding quotes
    // are added by ahc).
    def nameWithFilename = mp.fileName.fold(mp.name) { fn => s"""${mp.name}"; ${Part.FileNameDispositionParam}="$fn""" }

    val ctOrNull = mp.contentType.orNull

    val bodyPart = mp.body match {
      case StringBody(b, encoding, _) =>
        new StringPart(
          nameWithFilename,
          b,
          mp.contentType.getOrElse(MediaType.TextPlain.toString),
          Charset.forName(encoding)
        )
      case ByteArrayBody(b, _) =>
        new ByteArrayPart(nameWithFilename, b, ctOrNull)
      case ByteBufferBody(b, _) =>
        new ByteArrayPart(nameWithFilename, b.array(), ctOrNull)
      case InputStreamBody(b, _) =>
        // sadly async http client only supports parts that are strings,
        // byte arrays or files
        new ByteArrayPart(nameWithFilename, toByteArray(b), ctOrNull)
      case FileBody(b, _) =>
        new FilePart(mp.name, b.toFile, ctOrNull, null, mp.fileName.orNull)
    }

    bodyPart.setCustomHeaders(
      mp.headers.filterNot(_.is(HeaderNames.ContentType)).map(h => new Param(h.name, h.value)).toList.asJava
    )

    rb.addBodyPart(bodyPart)
  }
}
