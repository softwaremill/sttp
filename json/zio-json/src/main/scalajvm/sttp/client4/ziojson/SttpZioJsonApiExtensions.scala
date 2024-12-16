package sttp.client4.ziojson

import sttp.capabilities.Effect
import sttp.capabilities.zio.ZioStreams
import sttp.client4.DeserializationException
import sttp.client4.HttpError
import sttp.client4.ResponseException
import sttp.client4.StreamResponseAs
import sttp.client4.asStream
import zio.Task
import zio.ZIO
import zio.json.JsonDecoder
import zio.stream.ZPipeline

trait SttpZioJsonApiExtensions { this: SttpZioJsonApi =>
  def asJsonStream[B: JsonDecoder]
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
