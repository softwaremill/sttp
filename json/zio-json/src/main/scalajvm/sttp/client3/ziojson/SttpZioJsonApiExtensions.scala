package sttp.client3.ziojson

import sttp.capabilities.Effect
import sttp.capabilities.zio.ZioStreams
import sttp.client3.{DeserializationException, HttpError, IsOption, ResponseException, StreamResponseAs, asStream}
import zio.json.JsonDecoder
import zio.stream.ZPipeline
import zio.{Task, ZIO}

trait SttpZioJsonApiExtensions { this: SttpZioJsonApi =>
  def asJsonStream[B: JsonDecoder: IsOption]
      : StreamResponseAs[Either[ResponseException[String, String], B], ZioStreams with Effect[Task]] =
    asStream(ZioStreams)(s =>
      JsonDecoder[B]
        .decodeJsonStream(ZPipeline.utf8Decode.apply(s).mapChunks(_.flatMap(_.toCharArray)))
        .map(Right(_))
        .catchSome { case e => ZIO.left(DeserializationException("", e.getMessage)) }
    ).mapWithMetadata {
      case (Left(s), meta) => Left(HttpError(s, meta.code))
      case (Right(s), _)   => s
    }
}
