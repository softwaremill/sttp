package sttp.client3.akkahttp

import akka.http.scaladsl.model.{HttpHeader, HttpMethod, HttpMethods, HttpRequest}
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import sttp.client3.Request
import sttp.model.{Header, Method}

import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

private[akkahttp] object ToAkka {
  def request(r: Request[_, _]): Try[HttpRequest] = {
    val ar = HttpRequest(uri = r.uri.toString, method = method(r.method))
    ToAkka.headers(r.headers).map(ar.withHeaders)
  }

  def headers(headers: Seq[Header]): Try[Seq[HttpHeader]] = {
    // content-type and content-length headers have to be set via the body
    // entity, not as headers
    val parsed =
      headers
        .filterNot(Util.isContentType)
        .filterNot(Util.isContentLength)
        .map(h => HttpHeader.parse(h.name, h.value))
    val errors = parsed.collect { case ParsingResult.Error(e) =>
      e
    }
    if (errors.isEmpty) {
      val headers = parsed.collect { case ParsingResult.Ok(h, _) =>
        h
      }

      Success(headers.toList)
    } else {
      Failure(new RuntimeException(s"Cannot parse headers: $errors"))
    }
  }

  private def method(m: Method): HttpMethod =
    m match {
      case Method.GET     => HttpMethods.GET
      case Method.HEAD    => HttpMethods.HEAD
      case Method.POST    => HttpMethods.POST
      case Method.PUT     => HttpMethods.PUT
      case Method.DELETE  => HttpMethods.DELETE
      case Method.OPTIONS => HttpMethods.OPTIONS
      case Method.PATCH   => HttpMethods.PATCH
      case Method.CONNECT => HttpMethods.CONNECT
      case Method.TRACE   => HttpMethods.TRACE
      case _              => HttpMethod.custom(m.method)
    }
}
