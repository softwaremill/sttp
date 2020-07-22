package sttp.client.circe

import io.circe
import sttp.client._
import io.circe.parser.decode
import io.circe.{Decoder, Encoder, Printer}
import sttp.client.circe.SttpCirceApi.{=:!=, MergeableChoice, PartiallyMergeableChoice2}
import sttp.client.internal.Utf8
import sttp.model.MediaType

trait SttpCirceApi {

  implicit def circeBodySerializer[B](implicit
      encoder: Encoder[B],
      printer: Printer = Printer.noSpaces
  ): BodySerializer[B] =
    b => StringBody(encoder(b).pretty(printer), Utf8, Some(MediaType.ApplicationJson))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: Decoder: IsOption]
      : ResponseAs[Choice[HttpError[String], DeserializationError[io.circe.Error], B], Nothing] =
    ???

  def unsafeHttp[L <: Exception, M, R]: Choice[L, M, R] => Choice[Nothing, M, R] = {
    case Choice.Left(value)   => throw value
    case Choice.Middle(value) => Choice.Middle(value)
    case Choice.Right(value)  => Choice.Right(value)
  }

  def unsafeDeserialization[L, M <: Exception, R]: Choice[L, M, R] => Choice[L, Nothing, R] = {
    case Choice.Left(value)   => Choice.Left(value)
    case Choice.Middle(value) => throw value
    case Choice.Right(value)  => Choice.Right(value)
  }

  {
    val l: Either[Nothing, String] = ???
    l.merge
    import sttp.client.circe.SttpCirceApi._

    val value: ResponseAs[Choice[HttpError[String], DeserializationError[circe.Error], String], Nothing] =
      asJson[String]
    val value1 = value.map(unsafeHttp).merge2
    val value2 = asJson[String].map(unsafeDeserialization).merge2
    val value3 = asJson[String].map(unsafeHttp).map(unsafeDeserialization).merge2
  }

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: Decoder: IsOption]: ResponseAs[Either[DeserializationError[io.circe.Error], B], Nothing] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns the parse
    * result, or throws an exception is there's an error during deserialization
    */
  def asJsonAlwaysUnsafe[B: Decoder: IsOption]: ResponseAs[B, Nothing] =
    asStringAlways.map(ResponseAs.deserializeOrThrow(deserializeJson))

  def deserializeJson[B: Decoder: IsOption]: String => Either[io.circe.Error, B] =
    JsonInput.sanitize[B].andThen(decode[B])
}

object SttpCirceApi {
  trait =:!=[A, B]

  implicit def neq[A, B]: A =:!= B = new =:!=[A, B] {}
  implicit def neqAmbig1[A]: A =:!= A = ???
  implicit def neqAmbig2[A]: A =:!= A = ???

  class MergeableChoice[A, B](private val x: Choice[B, B, A])(implicit ev: B =:= A) {
    def merge: A =
      x match {
        case Choice.Right(a)  => a
        case Choice.Middle(a) => ev.apply(a)
        case Choice.Left(a)   => ev.apply(a)
      }
  }

  class PartiallyMergeableChoice2[A, B](private val x: Choice[B, B, A])(implicit ev: B =:!= A) {
    def merge: Either[B, A] =
      x match {
        case Choice.Right(a)  => Right(a)
        case Choice.Middle(a) => Left(a)
        case Choice.Left(a)   => Left(a)
      }
  }
  
  implicit class RichResponseAs2[A,B, +S](v: ResponseAs[Choice[B,B,A], S])(implicit ev: B =:= A) {
    def merge2: ResponseAs[A, S] = {
      v.map(s=> new MergeableChoice(s).merge)
    }
  }
  
  implicit class RichResponseAs3[A,B,+S](v:ResponseAs[Choice[B,B,A],S])(implicit ev: B =:!= A){
    def merge2: ResponseAs[Either[B,A], S] = {
      v.map(s=> new PartiallyMergeableChoice2(s).merge)
    }
  }
}

sealed abstract class Choice[+L, +M, +R]() {
  def triMap[L2, M2, R2](f: L => L2)(g: M => M2)(h: R => R2): Choice[L2, M2, R2] = {
    this match {
      case Choice.Left(value)   => Choice.Left(f(value))
      case Choice.Middle(value) => Choice.Middle(g(value))
      case Choice.Right(value)  => Choice.Right(h(value))
    }
  }
}

object Choice {
  case class Left[L](value: L) extends Choice[L, Nothing, Nothing]
  case class Middle[M](value: M) extends Choice[Nothing, M, Nothing]
  case class Right[R](value: R) extends Choice[Nothing, Nothing, R]
}

