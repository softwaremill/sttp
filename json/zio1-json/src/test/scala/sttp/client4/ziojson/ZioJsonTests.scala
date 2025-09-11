package sttp.client4.ziojson

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.json._
import sttp.client4._
import sttp.model._
import zio.Chunk
import zio.json.ast.Json
import sttp.client4.json.RunResponseAs
import sttp.client4.ResponseException.DeserializationException

class ZioJsonTests extends AnyFlatSpec with Matchers with EitherValues {

  "The ziojson module" should "encode arbitrary bodies given an encoder" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val req = basicRequest.body(asJson(body))
    extractBody(req) shouldBe expected
  }

  it should "decode arbitrary bodies given a decoder" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    RunResponseAs(responseAs)(body).right.value shouldBe expected
  }

  it should "decode None from empty body" in {
    val responseAs = asJson[Option[Inner]]

    RunResponseAs(responseAs)("").right.value shouldBe None
  }

  it should "decode Left(None) from empty body" in {
    import EitherDecoders._
    val responseAs = asJson[Either[Option[Inner], Outer]]

    RunResponseAs(responseAs)("").right.value shouldBe Left(None)
  }

  it should "decode Right(None) from empty body" in {
    import EitherDecoders._
    val responseAs = asJson[Either[Outer, Option[Inner]]]

    RunResponseAs(responseAs)("").right.value shouldBe Right(None)
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    val Left(DeserializationException(original, _, _)) = RunResponseAs(responseAs)(body)
    original shouldBe body
  }

  it should "include a non-null deserialization exception cause" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    val Left(DeserializationException(_, cause, _)) = RunResponseAs(responseAs)(body)
    cause.getMessage should not be null
  }

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]

    RunResponseAs(responseAs)("").left.value should matchPattern { case DeserializationException("", _, _) =>
    }
  }

  it should "read what it writes" in {
    val outer = Outer(Inner(42, true, "horses"), "cats")

    val encoded = extractBody(basicRequest.body(asJson(outer)))
    val decoded = RunResponseAs(asJson[Outer])(encoded)

    decoded.right.value shouldBe outer
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.body(asJson(body))

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some(MediaType.ApplicationJson.toString)
  }

  it should "only set the content type if it was not set earlier" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.contentType("horses/cats").body(asJson(body))

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some("horses/cats")
  }

  it should "serialize from Json.Obj" in {
    val fields: Chunk[(String, Json)] = Chunk(("location", Json.Str("hometown")), ("bio", Json.Str("Scala programmer")))
    val jObject: Json.Obj = Json.Obj(fields)
    val request = basicRequest.get(Uri("http://example.org")).body(asJson(jObject))

    val actualBody: String = request.body.show
    val actualContentType: Option[String] = request.contentType

    val expectedBody: String = "string: {\"location\":\"hometown\",\"bio\":\"Scala programmer\"}"
    val expectedContentType: Option[String] = Some("application/json")

    actualBody should be(expectedBody)
    actualContentType should be(expectedContentType)
  }

  it should "decode when using asJsonOrFail" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    RunResponseAs(asJsonOrFail[Outer])(body) shouldBe expected
  }

  it should "fail when using asJsonOrFail for incorrect JSON" in {
    val body = """invalid json"""

    assertThrows[DeserializationException] {
      RunResponseAs(asJsonOrFail[Outer])(body)
    }
  }

  it should "decode success when using asJsonEitherOrFail" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    RunResponseAs(asJsonEitherOrFail[Inner, Outer])(body) shouldBe Right(expected)
  }

  it should "decode failure when using asJsonEitherOrFail" in {
    val body = """{"a":21,"b":false,"c":"hippos"}"""
    val expected = Inner(21, false, "hippos")

    RunResponseAs(asJsonEitherOrFail[Inner, Outer], ResponseMetadata(StatusCode.BadRequest, "", Nil))(
      body
    ) shouldBe Left(expected)
  }

  def extractBody[T](request: PartialRequest[T]): String =
    request.body match {
      case StringBody(body, "utf-8", MediaType.ApplicationJson) =>
        body
      case wrongBody =>
        fail(s"Request body does not serialize to correct StringBody: $wrongBody")
    }

  object EitherDecoders {
    implicit def decoder[L: JsonDecoder, R: JsonDecoder]: JsonDecoder[Either[L, R]] =
      implicitly[JsonDecoder[L]] <+> implicitly[JsonDecoder[R]]
  }
}

case class Inner(a: Int, b: Boolean, c: String)

object Inner {
  implicit val encoder: JsonEncoder[Inner] = DeriveJsonEncoder.gen[Inner]
  implicit val codec: JsonDecoder[Inner] = DeriveJsonDecoder.gen[Inner]
}

case class Outer(foo: Inner, bar: String)

object Outer {
  implicit val encoder: JsonEncoder[Outer] = DeriveJsonEncoder.gen[Outer]
  implicit val codec: JsonDecoder[Outer] = DeriveJsonDecoder.gen[Outer]
}
