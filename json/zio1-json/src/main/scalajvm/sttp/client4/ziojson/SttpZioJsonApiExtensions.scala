package sttp.client4.ziojson

import sttp.capabilities.Effect
import sttp.capabilities.zio.ZioStreams
import sttp.client4.DeserializationException
import sttp.client4.HttpError
import sttp.client4.ResponseException
import sttp.client4.StreamResponseAs
import sttp.client4.asStream
import zio.RIO
import zio.ZIO
import zio.blocking.Blocking
import zio.json.JsonDecoder
import zio.stream.ZTransducer

trait SttpZioJsonApiExtensions { this: SttpZioJsonApi =>
  def asJsonStream[B: JsonDecoder]
      : StreamResponseAs[Either[ResponseException[String, String], B], ZioStreams with Effect[RIO[Blocking, *]]] =
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
