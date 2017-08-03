package com.softwaremill.sttp.akkahttp

import java.io.UnsupportedEncodingException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpEncodings, `Content-Type`}
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.model._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.collection.immutable.Seq

class AkkaHttpSttpHandler private (actorSystem: ActorSystem,
                                   ec: ExecutionContext,
                                   terminateActorSystemOnClose: Boolean)
    extends SttpHandler[Future, Source[ByteString, Any]] {

  // the supported stream type
  private type S = Source[ByteString, Any]

  private implicit val as = actorSystem
  private implicit val materializer = ActorMaterializer()

  override def send[T](r: Request[T, S]): Future[Response[T]] = {
    implicit val ec = this.ec
    requestToAkka(r)
      .flatMap(setBodyOnAkka(r, r.body, _))
      .toFuture
      .flatMap(Http().singleRequest(_))
      .flatMap { hr =>
        val code = hr.status.intValue()
        bodyFromAkka(r.responseAs, decodeAkkaResponse(hr))
          .map(Response(_, code, headersFromAkka(hr)))
      }
  }

  override def responseMonad: MonadError[Future] = new FutureMonad()(ec)

  private def methodToAkka(m: Method): HttpMethod = m match {
    case Method.GET     => HttpMethods.GET
    case Method.HEAD    => HttpMethods.HEAD
    case Method.POST    => HttpMethods.POST
    case Method.PUT     => HttpMethods.PUT
    case Method.DELETE  => HttpMethods.DELETE
    case Method.OPTIONS => HttpMethods.OPTIONS
    case Method.PATCH   => HttpMethods.PATCH
    case Method.CONNECT => HttpMethods.CONNECT
    case Method.TRACE   => HttpMethods.TRACE
    case _              => HttpMethod.custom(m.m)
  }

  private def bodyFromAkka[T](rr: ResponseAs[T, S],
                              hr: HttpResponse): Future[T] = {
    implicit val ec = this.ec

    def asByteArray =
      hr.entity.dataBytes
        .runFold(ByteString(""))(_ ++ _)
        .map(_.toArray[Byte])

    rr match {
      case MappedResponseAs(raw, g) => bodyFromAkka(raw, hr).map(g)

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

  private def headersFromAkka(hr: HttpResponse): Seq[(String, String)] = {
    val ch = ContentTypeHeader -> hr.entity.contentType.toString()
    val cl =
      hr.entity.contentLengthOption.map(ContentLengthHeader -> _.toString)
    val other = hr.headers.map(h => (h.name, h.value))
    ch :: (cl.toList ++ other)
  }

  private def requestToAkka(r: Request[_, S]): Try[HttpRequest] = {
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

      Success(ar.withHeaders(headers.toList))
    } else {
      Failure(new RuntimeException(s"Cannot parse headers: $errors"))
    }
  }

  private def setBodyOnAkka(r: Request[_, S],
                            body: RequestBody[S],
                            ar: HttpRequest): Try[HttpRequest] = {
    getContentTypeOrOctetStream(r).map { ct =>
      def doSet(body: RequestBody[S]): HttpRequest = body match {
        case NoBody => ar
        case StringBody(b, encoding) =>
          val ctWithEncoding = HttpCharsets
            .getForKey(encoding)
            .map(hc => ContentType.apply(ct.mediaType, () => hc))
            .getOrElse(ct)
          ar.withEntity(ctWithEncoding, b.getBytes(encoding))
        case ByteArrayBody(b)  => ar.withEntity(b)
        case ByteBufferBody(b) => ar.withEntity(ByteString(b))
        case InputStreamBody(b) =>
          ar.withEntity(
            HttpEntity(ct, StreamConverters.fromInputStream(() => b)))
        case PathBody(b)            => ar.withEntity(ct, b)
        case StreamBody(s)          => ar.withEntity(HttpEntity(ct, s))
        case SerializableBody(f, t) => doSet(f(t))
      }

      doSet(body)
    }
  }

  private def getContentTypeOrOctetStream(r: Request[_, S]): Try[ContentType] = {
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

  // http://doc.akka.io/docs/akka-http/10.0.7/scala/http/common/de-coding.html
  private def decodeAkkaResponse(response: HttpResponse): HttpResponse = {
    val decoder = response.encoding match {
      case HttpEncodings.gzip     => Gzip
      case HttpEncodings.deflate  => Deflate
      case HttpEncodings.identity => NoCoding
      case ce =>
        throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
    }

    decoder.decodeMessage(response)
  }

  override def close(): Unit = {
    if (terminateActorSystemOnClose) actorSystem.terminate()
  }

  private implicit class RichTry[T](t: Try[T]) {
    def toFuture: Future[T] = t match {
      case Success(v) => Future.successful(v)
      case Failure(v) => Future.failed(v)
    }
  }
}

object AkkaHttpSttpHandler {

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def apply()(implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[Future, Source[ByteString, Any]] =
    new AkkaHttpSttpHandler(ActorSystem("sttp"), ec, true)

  /**
    * @param actorSystem The actor system which will be used for the http-client
    *                    actors.
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingActorSystem(actorSystem: ActorSystem)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[Future, Source[ByteString, Any]] =
    new AkkaHttpSttpHandler(actorSystem, ec, false)
}
