package com.softwaremill.sttp.akkahttp

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.model._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class AkkaHttpSttpHandler(actorSystem: ActorSystem)
    extends SttpHandler[Future, Source[ByteString, Any]] {

  def this() = this(ActorSystem("sttp"))

  private implicit val as = actorSystem
  private implicit val materializer = ActorMaterializer()
  import as.dispatcher

  override def send[T](r: Request,
                       responseAs: ResponseAs[T, Source[ByteString, Any]])
    : Future[Response[T]] = {
    requestToAkka(r)
      .map(setBodyOnAkka(r, r.body, _).get)
      .flatMap(Http().singleRequest(_))
      .flatMap { hr =>
        val code = hr.status.intValue()
        bodyFromAkka(responseAs, hr).map(Response(code, _))
      }
  }

  private def methodToAkka(m: Method): HttpMethod = m match {
    case Method.GET => HttpMethods.GET
    case Method.HEAD => HttpMethods.HEAD
    case Method.POST => HttpMethods.POST
    case Method.PUT => HttpMethods.PUT
    case Method.DELETE => HttpMethods.DELETE
    case Method.OPTIONS => HttpMethods.OPTIONS
    case Method.PATCH => HttpMethods.PATCH
    case Method.CONNECT => HttpMethods.CONNECT
    case Method.TRACE => HttpMethods.TRACE
    case _ => HttpMethod.custom(m.m)
  }

  private def bodyFromAkka[T](rr: ResponseAs[T, Source[ByteString, Any]],
                              hr: HttpResponse): Future[T] = {
    def asByteArray =
      hr.entity.dataBytes
        .runFold(ByteString(""))(_ ++ _)
        .map(_.toArray[Byte])

    rr match {
      case IgnoreResponse =>
        hr.discardEntityBytes()
        Future.successful(())

      case ResponseAsString(enc) =>
        asByteArray.map(new String(_, enc))

      case ResponseAsByteArray =>
        asByteArray

      case r @ ResponseAsStream() =>
        Future.successful(r.responseIsStream(hr.entity.dataBytes))
    }
  }

  private def requestToAkka(r: Request): Future[HttpRequest] = {
    val ar = HttpRequest(uri = r.uri.toString, method = methodToAkka(r.method))
    val parsed =
      r.headers.filterNot(isContentType).map(h => HttpHeader.parse(h._1, h._2))
    val errors = parsed.collect {
      case ParsingResult.Error(e) => e
    }
    if (errors.isEmpty) {
      val headers = parsed.collect {
        case ParsingResult.Ok(h, _) => h
      }

      Future.successful(ar.withHeaders(headers.toList))
    } else {
      Future.failed(new RuntimeException(s"Cannot parse headers: $errors"))
    }
  }

  private def setBodyOnAkka(r: Request,
                            body: RequestBody,
                            ar: HttpRequest): Try[HttpRequest] = {
    getContentTypeOrOctetStream(r).map { ct =>
      def doSet(body: RequestBody): HttpRequest = body match {
        case NoBody => ar
        case StringBody(b, encoding) =>
          val ctWithEncoding = HttpCharsets
            .getForKey(encoding)
            .map(hc => ContentType.apply(ct.mediaType, () => hc))
            .getOrElse(ct)
          ar.withEntity(ctWithEncoding, b.getBytes(encoding))
        case ByteArrayBody(b) => ar.withEntity(b)
        case ByteBufferBody(b) => ar.withEntity(ByteString(b))
        case InputStreamBody(b) =>
          ar.withEntity(
            HttpEntity(ct, StreamConverters.fromInputStream(() => b)))
        case FileBody(b) => ar.withEntity(ct, b.toPath)
        case PathBody(b) => ar.withEntity(ct, b)
        case s @ SerializableBody(_, _) => doSetSerializable(s)
      }

      def doSetSerializable[T](body: SerializableBody[T]): HttpRequest =
        body match {
          case SerializableBody(SourceBodySerializer, t) =>
            ar.withEntity(HttpEntity(ct, t))
          case SerializableBody(f, t) => doSet(f(t))
        }

      doSet(body)
    }
  }

  private def getContentTypeOrOctetStream(r: Request): Try[ContentType] = {
    r.headers
      .find(isContentType)
      .map(_._2)
      .map { ct =>
        ContentType
          .parse(ct)
          .fold(
            errors =>
              Failure(
                new RuntimeException(s"Cannot parse content type: $errors")),
            Success(_))
      }
      .getOrElse(Success(`application/octet-stream`))
  }

  private def isContentType(header: (String, String)) =
    header._1.toLowerCase.contains(`Content-Type`.lowercaseName)

  def close(): Future[Terminated] = {
    actorSystem.terminate()
  }
}
