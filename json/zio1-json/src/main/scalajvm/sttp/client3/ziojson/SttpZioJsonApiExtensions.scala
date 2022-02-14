package sttp.client3.ziojson

import sttp.capabilities.Effect
import sttp.capabilities.zio.ZioStreams
import sttp.client3.{DeserializationException, HttpError, IsOption, ResponseAs, ResponseException, asStream}
import zio.blocking.Blocking
import zio.json.JsonDecoder
import zio.stream.ZTransducer
import zio.{RIO, ZIO}

trait SttpZioJsonApiExtensions { this: SttpZioJsonApi =>
  def asJsonStream[B: JsonDecoder: IsOption]
      : ResponseAs[Either[ResponseException[String, String], B], Effect[RIO[Blocking, *]] with ZioStreams] =
    asStream(ZioStreams)(s =>
      JsonDecoder[B]
        .decodeJsonStream(s >>> ZTransducer.utf8Decode.mapChunks(_.flatMap(_.toCharArray)))
        .map(Right(_))
        .catchSome { case e => ZIO.left(DeserializationException("", e.getMessage)) }
    ).mapWithMetadata {
      case (Left(s), meta) => Left(HttpError(s, meta.code))
      case (Right(s), _)   => s
    }
}
