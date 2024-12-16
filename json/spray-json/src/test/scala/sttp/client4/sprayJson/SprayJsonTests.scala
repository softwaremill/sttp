package sttp.client4.sprayJson

import org.scalatest.EitherValues
import spray.json.DefaultJsonProtocol._
import spray.json.{DeserializationException => _, _}
import sttp.client4.internal.Utf8
import sttp.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.basicRequest
import sttp.client4.PartialRequest
import sttp.client4.StringBody
import sttp.client4.Request
import sttp.client4.DeserializationException
import spray.json.JsonParser.ParsingException
import sttp.client4.json.RunResponseAs

class SprayJsonTests extends AnyFlatSpec with Matchers with EitherValues {
  import SprayJsonTests._

  behavior of "The spray-json module"

  // implicit private val jsObjectSerializer: BodySerializer[JsObject] = sprayBodySerializer(RootJsObjectFormat)

  it should "encode arbitrary json bodies" in {
    val body = Outer(Inner(42, true, "horses"), "cats")

    val req = basicRequest.body(asJson(body))

    extractBody(req) should include(""""foo":{"a":42,"b":true,"c":"horses"}""")
    extractBody(req) should include(""""bar":"cats"""")
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

    RunResponseAs(responseAs)("") should matchPattern { case Left(DeserializationException(_, _: ParsingException)) =>
    }
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    RunResponseAs(responseAs)(body) should matchPattern { case Left(DeserializationException(_, _: ParsingException)) =>
    }
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.body(asJson(body))

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some(MediaType.ApplicationJson.copy(charset = Some(Utf8)).toString)
  }

  it should "serialize from JsObject using implicit sprayBodySerializer" in {
    val json: JsObject = JsObject(
      "location" -> "hometown".toJson,
      "bio" -> "Scala programmer".toJson
    )
    val request: Request[Either[String, String]] = basicRequest.get(Uri("http://example.org")).body(asJson(json))

    val actualBody: String = request.body.show
    val actualContentType: Option[String] = request.contentType

    val expectedBody: String = "string: {\"bio\":\"Scala programmer\",\"location\":\"hometown\"}"
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

    assertThrows[DeserializationException[Exception]] {
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

object SprayJsonTests {
  case class Inner(a: Int, b: Boolean, c: String)

  object Inner {
    implicit val jsonFormat: RootJsonFormat[Inner] = jsonFormat3(Inner.apply)
  }

  case class Outer(foo: Inner, bar: String)

  object Outer {
    implicit val jsonFormat: RootJsonFormat[Outer] = jsonFormat2(Outer.apply)
  }
}
