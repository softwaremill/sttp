package sttp.client4.pekkohttp

import org.apache.pekko
import pekko.http.scaladsl.model.{
  ContentType,
  HttpCharsets,
  HttpEntity,
  HttpRequest,
  MediaType,
  Multipart => PekkoMultipart,
  RequestEntity
}
import pekko.stream.scaladsl.{Source, StreamConverters}
import pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4._
import sttp.model.Part

import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}
import sttp.client4.compression.Compressor

private[pekkohttp] object BodyToPekko {
  def apply[R](
      r: GenericRequest[_, R],
      ar: HttpRequest,
      compressors: List[Compressor[R]]
  ): Try[HttpRequest] = {
    def ctWithCharset(ct: ContentType, charset: String) =
      HttpCharsets
        .getForKey(charset)
        .map(hc => ContentType.apply(ct.mediaType, () => hc))
        .getOrElse(ct)

    val (body, contentLength) = Compressor.compressIfNeeded(r, compressors)

    def toBodyPart(mp: Part[BodyPart[_]]): Try[PekkoMultipart.FormData.BodyPart] = {
      def streamPartEntity(contentType: ContentType, s: PekkoStreams.BinaryStream) =
        mp.contentLength match {
          case None    => HttpEntity.IndefiniteLength(contentType, s)
          case Some(l) => HttpEntity(contentType, l, s)
        }

      def entity(ct: ContentType) =
        mp.body match {
          case StringBody(b, encoding, _) => HttpEntity(ctWithCharset(ct, encoding), b.getBytes(encoding))
          case ByteArrayBody(b, _)        => HttpEntity(ct, b)
          case ByteBufferBody(b, _)       => HttpEntity(ct, ByteString(b))
          case isb: InputStreamBody       => streamPartEntity(ct, StreamConverters.fromInputStream(() => isb.b))
          case FileBody(b, _)             => HttpEntity.fromPath(ct, b.toPath)
          case StreamBody(b)              => streamPartEntity(ct, b.asInstanceOf[PekkoStreams.BinaryStream])
        }

      for {
        ct <- Util.parseContentTypeOrOctetStream(mp.contentType)
        headers <- ToPekko.headers(mp.headers.toList)
      } yield PekkoMultipart.FormData.BodyPart(mp.name, entity(ct), mp.dispositionParams, headers)
    }

    def streamEntity(contentType: ContentType, s: PekkoStreams.BinaryStream) =
      contentLength match {
        case None    => HttpEntity(contentType, s)
        case Some(l) => HttpEntity(contentType, l, s)
      }

    Util.parseContentTypeOrOctetStream(r).flatMap { ct =>
      body match {
        case NoBody                     => Success(ar)
        case StringBody(b, encoding, _) => Success(ar.withEntity(ctWithCharset(ct, encoding), b.getBytes(encoding)))
        case ByteArrayBody(b, _)        => Success(ar.withEntity(HttpEntity(ct, b)))
        case ByteBufferBody(b, _)       => Success(ar.withEntity(HttpEntity(ct, ByteString(b))))
        case InputStreamBody(b, _)      =>
          Success(ar.withEntity(streamEntity(ct, StreamConverters.fromInputStream(() => b))))
        case FileBody(b, _)      => Success(ar.withEntity(ct, b.toPath))
        case StreamBody(s)       => Success(ar.withEntity(streamEntity(ct, s.asInstanceOf[PekkoStreams.BinaryStream])))
        case m: MultipartBody[_] =>
          Util
            .traverseTry(m.parts.map(toBodyPart))
            .flatMap(bodyParts => multipartEntity(r, bodyParts).map(ar.withEntity))
      }
    }
  }

  private def multipartEntity(
      r: GenericRequest[_, _],
      bodyParts: Seq[PekkoMultipart.FormData.BodyPart]
  ): Try[RequestEntity] =
    r.headers.find(Util.isContentType) match {
      case None     => Success(PekkoMultipart.FormData(bodyParts: _*).toEntity)
      case Some(ct) =>
        Util.parseContentType(ct.value).map(_.mediaType).flatMap {
          case m: MediaType.Multipart =>
            Success(
              PekkoMultipart
                .General(m, Source(bodyParts.map(bp => PekkoMultipart.General.BodyPart(bp.entity, bp.headers))))
                .toEntity
            )
          case _ => Failure(new RuntimeException(s"Non-multipart content type: $ct"))
        }
    }
}
