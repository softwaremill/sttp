package sttp.client4.circe

import io.circe._
import org.scalatest._
import sttp.client4.internal._
import sttp.client4._
import sttp.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.json.RunResponseAs

class CirceTests extends AnyFlatSpec with Matchers with EitherValues {

  "The circe module" should "encode arbitrary bodies given an encoder" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = basicRequest.body(asJson(body))

    extractBody(req) shouldBe expected
  }

  it should "use the given printer" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    implicit val printer = Printer.spaces4

    val req = basicRequest.body(asJson(body))

    extractBody(req) should include("\n    \"foo")
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

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]

    RunResponseAs(responseAs)("").left.value should matchPattern {
      case DeserializationException("", _: io.circe.ParsingFailure) =>
    }
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    val Left(DeserializationException(original, _)) = RunResponseAs(responseAs)(body)
    original shouldBe body
  }

  it should "encode and decode back to the same thing" in {
    val outer = Outer(Inner(42, true, "horses"), "cats")

    val encoded = extractBody(basicRequest.body(asJson(outer)))
    val decoded = RunResponseAs(asJson[Outer])(encoded)

    decoded.right.value shouldBe outer
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.body(asJson(body))

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some(MediaType.ApplicationJson.copy(charset = Some(Utf8)).toString)
  }

  it should "only set the content type if it was not set earlier" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.contentType("horses/cats").body(asJson(body))

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some("horses/cats")
  }

  it should "serialize from JsonObject" in {
    import io.circe.syntax.EncoderOps
    import io.circe.JsonObject
    import sttp.model.Uri

    val jObject: JsonObject = JsonObject(("location", "hometown".asJson), ("bio", "Scala programmer".asJson))
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

    assertThrows[DeserializationException[io.circe.Error]] {
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

  case class Inner(a: Int, b: Boolean, c: String)

  object Inner {
    implicit val encoder: Encoder[Inner] =
      Encoder.forProduct3("a", "b", "c")(i => (i.a, i.b, i.c))
    implicit val decoder: Decoder[Inner] =
      Decoder.forProduct3("a", "b", "c")(Inner.apply)
  }

  case class Outer(foo: Inner, bar: String)

  object Outer {
    implicit val encoder: Encoder[Outer] =
      Encoder.forProduct2("foo", "bar")(o => (o.foo, o.bar))
    implicit val decoder: Decoder[Outer] =
      Decoder.forProduct2("foo", "bar")(Outer.apply)
  }

  object EitherDecoders {
    implicit def decoder[L: Decoder, R: Decoder]: Decoder[Either[L, R]] =
      implicitly[Decoder[L]].either(implicitly[Decoder[R]])
  }

  def extractBody[T](request: PartialRequest[T]): String =
    request.body match {
      case StringBody(body, "utf-8", MediaType.ApplicationJson) =>
        body
      case wrongBody =>
        fail(s"Request body does not serialize to correct StringBody: $wrongBody")
    }
}
