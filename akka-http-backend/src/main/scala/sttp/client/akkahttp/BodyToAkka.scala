package sttp.client.akkahttp

import akka.http.scaladsl.model.{
  ContentType,
  HttpCharsets,
  HttpEntity,
  HttpRequest,
  MediaType,
  RequestEntity,
  Multipart => AkkaMultipart
}
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
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
  StringBody
}
import sttp.model.Part

import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

private[akkahttp] object BodyToAkka {
  def apply[R](
      r: Request[_, R],
      body: RequestBody[R],
      ar: HttpRequest
  ): Try[HttpRequest] = {
    def ctWithCharset(ct: ContentType, charset: String) =
      HttpCharsets
        .getForKey(charset)
        .map(hc => ContentType.apply(ct.mediaType, () => hc))
        .getOrElse(ct)

    def toBodyPart(mp: Part[BasicRequestBody]): Try[AkkaMultipart.FormData.BodyPart] = {
      def entity(ct: ContentType) =
        mp.body match {
          case StringBody(b, encoding, _) =>
            HttpEntity(ctWithCharset(ct, encoding), b.getBytes(encoding))
          case ByteArrayBody(b, _)  => HttpEntity(ct, b)
          case ByteBufferBody(b, _) => HttpEntity(ct, ByteString(b))
          case isb: InputStreamBody =>
            HttpEntity
              .IndefiniteLength(ct, StreamConverters.fromInputStream(() => isb.b))
          case FileBody(b, _) => HttpEntity.fromPath(ct, b.toPath)
        }

      for {
        ct <- Util.parseContentTypeOrOctetStream(mp.contentType)
        headers <- ToAkka.headers(mp.headers.toList)
      } yield {
        AkkaMultipart.FormData.BodyPart(mp.name, entity(ct), mp.dispositionParams, headers)
      }
    }

    Util.parseContentTypeOrOctetStream(r).flatMap { ct =>
      body match {
        case NoBody => Success(ar)
        case StringBody(b, encoding, _) =>
          Success(ar.withEntity(ctWithCharset(ct, encoding), b.getBytes(encoding)))
        case ByteArrayBody(b, _) => Success(ar.withEntity(HttpEntity(ct, b)))
        case ByteBufferBody(b, _) =>
          Success(ar.withEntity(HttpEntity(ct, ByteString(b))))
        case InputStreamBody(b, _) =>
          Success(ar.withEntity(HttpEntity(ct, StreamConverters.fromInputStream(() => b))))
        case FileBody(b, _) => Success(ar.withEntity(ct, b.toPath))
        case StreamBody(s)  => Success(ar.withEntity(HttpEntity(ct, s.asInstanceOf[AkkaStreams.BinaryStream])))
        case MultipartBody(ps) =>
          Util
            .traverseTry(ps.map(toBodyPart))
            .flatMap(bodyParts => multipartEntity(r, bodyParts).map(ar.withEntity))
      }
    }
  }

  private def multipartEntity(r: Request[_, _], bodyParts: Seq[AkkaMultipart.FormData.BodyPart]): Try[RequestEntity] = {
    r.headers.find(Util.isContentType) match {
      case None => Success(AkkaMultipart.FormData(bodyParts: _*).toEntity())
      case Some(ct) =>
        Util.parseContentType(ct.value).map(_.mediaType).flatMap {
          case m: MediaType.Multipart =>
            Success(
              AkkaMultipart
                .General(m, Source(bodyParts.map { bp => AkkaMultipart.General.BodyPart(bp.entity, bp.headers) }))
                .toEntity()
            )
          case _ => Failure(new RuntimeException(s"Non-multipart content type: $ct"))
        }
    }
  }
}
