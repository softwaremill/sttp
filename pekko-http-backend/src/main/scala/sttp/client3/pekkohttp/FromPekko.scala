package sttp.client3.pekkohttp

import org.apache.pekko.http.scaladsl.model.HttpResponse
import sttp.client3.{Request, SttpClientException}
import sttp.model.{Header, HeaderNames}

import scala.collection.immutable.Seq

private[pekkohttp] object FromPekko {
  def headers(hr: HttpResponse): Seq[Header] = {
    val ch = Header(HeaderNames.ContentType, hr.entity.contentType.toString())
    val cl =
      hr.entity.contentLengthOption.map(v => Header.contentLength(v))
    val other = hr.headers.map(h => Header(h.name, h.value))
    ch :: (cl.toList ++ other)
  }

  def exception(request: Request[_, _], e: Exception): Option[Exception] =
    e match {
      case e: org.apache.pekko.stream.ConnectionException => Some(new SttpClientException.ConnectException(request, e))
      case e: org.apache.pekko.stream.StreamTcpException =>
        e.getCause match {
          case ee: Exception =>
            exception(request, ee).orElse(Some(new SttpClientException.ReadException(request, e)))
          case _ => Some(new SttpClientException.ReadException(request, e))
        }
      case e: org.apache.pekko.stream.scaladsl.TcpIdleTimeoutException => Some(new SttpClientException.TimeoutException(request, e))
      case e: Exception => SttpClientException.defaultExceptionToSttpClientException(request, e)
    }
}
