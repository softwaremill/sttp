package com.softwaremill.sttp.akkahttp

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.model._

import scala.concurrent.Future

class AkkaHttpSttpHandler(actorSystem: ActorSystem) extends SttpStreamHandler[Future, Source[ByteString, Any]] {
  def this() = this(ActorSystem("sttp"))

  private implicit val as = actorSystem
  private implicit val materializer = ActorMaterializer()
  import as.dispatcher

  override def send[T](r: Request, responseAs: ResponseAs[T]): Future[Response[T]] = {
    requestToAkka(r).flatMap(setBody(r, r.body, _)).flatMap(Http().singleRequest(_)).flatMap { hr =>
      val code = hr.status.intValue()
      bodyFromAkkaResponse(responseAs, hr).map(Response(code, _))
    }
  }

  override def send(r: Request, responseAsStream: ResponseAsStream[Source[ByteString, Any]]): Future[Response[Source[ByteString, Any]]] = {
    requestToAkka(r).flatMap(setBody(r, r.body, _)).flatMap(Http().singleRequest(_)).map { hr =>
      val code = hr.status.intValue()
      Response(code, hr.entity.dataBytes)
    }
  }

  private def convertMethod(m: Method): HttpMethod = m match {
    case Method.GET => HttpMethods.GET
    case Method.POST => HttpMethods.POST
    case _ => HttpMethod.custom(m.m)
  }

  private def bodyFromAkkaResponse[T](rr: ResponseAs[T], hr: HttpResponse): Future[T] = {
    def asByteArray = hr.entity.dataBytes
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
    }
  }

  private def requestToAkka(r: Request): Future[HttpRequest] = {
    val ar = HttpRequest(uri = r.uri.toString, method = convertMethod(r.method))
    val parsed = r.headers.map(h => HttpHeader.parse(h._1, h._2))
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

  private def setBody(r: Request, body: RequestBody, ar: HttpRequest): Future[HttpRequest] = body match {
    case NoBody => Future.successful(ar)
    case StringBody(b, encoding) => Future.successful(ar.withEntity(b)) // TODO
    case ByteArrayBody(b) => Future.successful(ar.withEntity(b))
    case ByteBufferBody(b) => Future.successful(ar.withEntity(ByteString(b)))
    case InputStreamBody(b) => Future.successful(ar) //TODO
    case FileBody(b) =>  Future.successful(ar)//TODO
    case PathBody(b) => Future.successful(ar)       //TODO
    case sb@SerializableBody(_, _) => setSerializableBody(r, sb, ar)
  }

  private def setSerializableBody[T](r: Request, body: SerializableBody[T], ar: HttpRequest): Future[HttpRequest] = body match {
    case SerializableBody(SourceBodySerializer, t) =>
      getContentTypeOrOctetStream(r).map(ct => ar.withEntity(HttpEntity(ct, t)))

    case SerializableBody(f, t) => setBody(r, f(t), ar)
  }

  private def getContentTypeOrOctetStream(r: Request): Future[ContentType] = {
    r.headers
      .find(_._1.toLowerCase.contains(`Content-Type`.lowercaseName))
      .map(_._2)
      .map { ct =>
        ContentType.parse(ct).fold(
          errors => Future.failed(new RuntimeException(s"Cannot parse content type: $errors")),
          Future.successful)
      }
      .getOrElse(Future.successful(`application/octet-stream`))
  }

  def close(): Future[Terminated] = {
    actorSystem.terminate()
  }
}