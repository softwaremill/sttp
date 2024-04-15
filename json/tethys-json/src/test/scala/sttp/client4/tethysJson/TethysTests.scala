package sttp.client4.tethysJson

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.internal._
import sttp.model._
import tethys.derivation.semiauto.{jsonReader, jsonWriter}
import tethys.jackson.{jacksonTokenIteratorProducer, jacksonTokenWriterProducer}
import tethys.readers.tokens.TokenIterator
import tethys.readers.{FieldName, ReaderError}
import tethys.{JsonReader, JsonWriter}

import scala.util.{Failure, Success, Try}

class TethysTests extends AnyFlatSpec with Matchers with EitherValues {

  "The tethys module" should "encode arbitrary bodies given an encoder" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = basicRequest.body(body)

    extractBody(req, MediaType.ApplicationJson) shouldBe expected
  }

  it should "decode arbitrary bodies given a decoder" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body).right.value shouldBe expected
  }

  it should "decode None from empty body" in {
    val responseAs = asJson[Option[Inner]]

    runJsonResponseAs(responseAs)("").right.value shouldBe None
  }

  it should "decode Left(None) from empty body" in {
    import EitherDecoders._
    val responseAs = asJson[Either[Option[Inner], Outer]]

    runJsonResponseAs(responseAs)("").right.value shouldBe Left(None)
  }

  it should "decode Right(None) from empty body" in {
    import EitherDecoders._
    val responseAs = asJson[Either[Outer, Option[Inner]]]

    runJsonResponseAs(responseAs)("").right.value shouldBe Right(None)
  }

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]

    runJsonResponseAs(responseAs)("") should matchPattern { case Left(DeserializationException("", _: ReaderError)) =>
    }
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    val Left(DeserializationException(original, _)) = runJsonResponseAs(responseAs)(body)
    original shouldBe body
  }

  it should "encode and decode back to the same thing" in {
    val outer = Outer(Inner(42, true, "horses"), "cats")

    val encoded = extractBody(basicRequest.body(outer), MediaType.ApplicationJson)
    val decoded = runJsonResponseAs(asJson[Outer])(encoded)

    decoded.right.value shouldBe outer
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.body(body)

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some(MediaType.ApplicationJson.copy(charset = Some(Utf8)).toString)
  }

  it should "only set the content type if it was not set earlier" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.contentType("horses/cats").body(body)

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some("horses/cats")
  }

  case class Inner(a: Int, b: Boolean, c: String)

  object Inner {
    implicit val encoder: JsonWriter[Inner] = jsonWriter
    implicit val decoder: JsonReader[Inner] = jsonReader
  }

  case class Outer(foo: Inner, bar: String)

  object Outer {
    implicit val encoder: JsonWriter[Outer] = jsonWriter
    implicit val decoder: JsonReader[Outer] = jsonReader
  }

  object EitherDecoders {
    implicit def decoder[L: JsonReader, R: JsonReader]: JsonReader[Either[L, R]] = new JsonReader[Either[L, R]] {

      override def read(it: TokenIterator)(implicit fieldName: FieldName): Either[L, R] = {
        val newIt = it.collectExpression()
        (
          Try(implicitly[JsonReader[L]].read(newIt.copy())),
          Try(implicitly[JsonReader[R]].read(newIt))
        ) match {
          case (Success(value), Failure(_)) => Left(value)
          case (Failure(_), Success(value)) => Right(value)
          case (Success(_), Success(_)) =>
            ReaderError.wrongJson("Both succeeded.")
          case (Failure(exceptionLeft), Failure(exceptionRight)) =>
            ReaderError.wrongJson(
              s"Either parse exception. Both parsers failed: ${exceptionLeft.getMessage} and ${exceptionRight.getMessage}"
            )
        }
      }
    }
  }

  def extractBody[T](request: PartialRequest[T], mediaType: MediaType): String =
    request.body match {
      case StringBody(body, "utf-8", `mediaType`) =>
        body
      case wrongBody =>
        fail(s"Request body does not serialize to correct StringBody: $wrongBody")
    }

  def runJsonResponseAs[A](responseAs: ResponseAs[A]): String => A =
    responseAs.delegate match {
      case responseAs: MappedResponseAs[_, A, Nothing] =>
        responseAs.raw match {
          case ResponseAsByteArray =>
            s => responseAs.g(s.getBytes(Utf8), ResponseMetadata(StatusCode.Ok, "", Seq.empty))
          case _ =>
            fail("MappedResponseAs does not wrap a ResponseAsByteArray")
        }
      case _ => fail("ResponseAs is not a MappedResponseAs")
    }

}
