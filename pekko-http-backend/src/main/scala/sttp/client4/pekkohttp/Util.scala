package sttp.client4.pekkohttp

import org.apache.pekko
import pekko.http.scaladsl.model.ContentType
import pekko.http.scaladsl.model.ContentTypes.`application/octet-stream`
import pekko.http.scaladsl.model.headers.{`Content-Length`, `Content-Type`}
import sttp.client4.GenericRequest
import sttp.model.Header

import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

private[pekkohttp] object Util {
  def traverseTry[T](l: Seq[Try[T]]): Try[Seq[T]] = {
    // https://stackoverflow.com/questions/15495678/flatten-scala-try
    val (ss: Seq[Success[T]] @unchecked, fs: Seq[Failure[T]] @unchecked) =
      l.partition(_.isSuccess)

    if (fs.isEmpty) Success(ss.map(_.get))
    else Failure[Seq[T]](fs.head.exception)
  }

  def parseContentTypeOrOctetStream(r: GenericRequest[_, _]): Try[ContentType] =
    parseContentTypeOrOctetStream(
      r.headers
        .find(isContentType)
        .map(_.value)
    )

  def parseContentTypeOrOctetStream(ctHeader: Option[String]): Try[ContentType] =
    ctHeader
      .map(parseContentType)
      .getOrElse(Success(`application/octet-stream`))

  def parseContentType(ctHeader: String): Try[ContentType] =
    ContentType
      .parse(ctHeader)
      .fold(errors => Failure(new RuntimeException(s"Cannot parse content type: $errors")), Success(_))

  def isContentType(header: Header): Boolean =
    header.name.toLowerCase.contains(`Content-Type`.lowercaseName)

  def isContentLength(header: Header): Boolean =
    header.name.toLowerCase.contains(`Content-Length`.lowercaseName)
}
