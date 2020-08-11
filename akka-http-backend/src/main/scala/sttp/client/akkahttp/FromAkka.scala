package sttp.client.akkahttp

import akka.http.scaladsl.model.HttpResponse
import sttp.client.{Request, SttpClientException}
import sttp.model.{Header, HeaderNames}

import scala.collection.immutable.Seq

private[akkahttp] object FromAkka {
  def headers(hr: HttpResponse): Seq[Header] = {
    val ch = Header(HeaderNames.ContentType, hr.entity.contentType.toString())
    val cl =
      hr.entity.contentLengthOption.map(v => Header.contentLength(v))
    val other = hr.headers.map(h => Header(h.name, h.value))
    ch :: (cl.toList ++ other)
  }

  def exception(request: Request[_, _], e: Exception): Option[Exception] =
    e match {
      case e: akka.stream.ConnectionException => Some(new SttpClientException.ConnectException(request, e))
      case e: akka.stream.StreamTcpException =>
        e.getCause match {
          case ee: Exception =>
            exception(request, ee).orElse(Some(new SttpClientException.ReadException(request, e)))
          case _ => Some(new SttpClientException.ReadException(request, e))
        }
      case e: akka.stream.scaladsl.TcpIdleTimeoutException => Some(new SttpClientException.ReadException(request, e))
      case e: NotAWebsocketException                       => Some(new SttpClientException.ReadException(request, e))
      case e: Exception                                    => SttpClientException.defaultExceptionToSttpClientException(request, e)
    }
}
