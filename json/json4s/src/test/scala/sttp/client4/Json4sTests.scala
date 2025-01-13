package sttp.client4

import org.json4s.JsonAST.JString
import org.json4s.ParserUtil.ParseException
import org.json4s.native.Serialization
import org.json4s.{native, DefaultFormats, JField, JObject, MappingException}
import org.scalatest._
import sttp.client4.internal._
import sttp.model._

import scala.language.higherKinds
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.json.RunResponseAs

class Json4sTests extends AnyFlatSpec with Matchers with EitherValues {
  implicit val serialization: Serialization.type = native.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  import Json4sTests._
  import json4s._

  "The json4s module" should "encode arbitrary json bodies" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = basicRequest.body(asJson(body))

    extractBody(req) shouldBe expected
  }

  it should "decode arbitrary bodies" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    RunResponseAs(responseAs)(body) shouldBe Right(expected)
  }

  it should "decode None from empty body" in {
    val responseAs = asJson[Option[Inner]]

    RunResponseAs(responseAs)("") shouldBe Right(None)
  }

  it should "decode Left(None) from empty body" in {
    val responseAs = asJson[Either[Option[Inner], Outer]]

    RunResponseAs(responseAs)("") shouldBe Right(Left(None))
  }

  it should "decode Right(None) from empty body" in {
    val responseAs = asJson[Either[Outer, Option[Inner]]]

    RunResponseAs(responseAs)("") shouldBe Right(Right(None))
  }

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]

    RunResponseAs(responseAs)("") should matchPattern {
      case Left(DeserializationException(_, _: MappingException, _)) =>
    }
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    RunResponseAs(responseAs)(body) should matchPattern {
      case Left(DeserializationException(_, _: ParseException, _)) =>
    }
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.body(asJson(body))

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some(MediaType.ApplicationJson.copy(charset = Some(Utf8)).toString)
  }

  it should "serialize from JObject using" in {
    val jObject: JObject = JObject(JField("location", JString("hometown")), JField("bio", JString("Scala programmer")))
    val request: Request[Either[String, String]] = basicRequest.get(Uri("http://example.org")).body(asJson(jObject))

    val actualBody: String = request.body.show
    val actualContentType: Option[String] = request.contentType

    val expectedBody: String = "string: {\"location\":\"hometown\",\"bio\":\"Scala programmer\"}"
    val expectedContentType: Option[String] = Some("application/json; charset=utf-8")

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

    assertThrows[Exception] {
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
}

object Json4sTests {
  case class Inner(a: Int, b: Boolean, c: String)
  case class Outer(foo: Inner, bar: String)
}
