package sttp.client4

import sttp.client4.playJson._
import sttp.model._
import play.api.libs.json._
import org.scalatest._
import sttp.model.StatusCode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.json.RunResponseAs
import sttp.client4.ResponseException.DeserializationException

class PlayJsonTests extends AnyFlatSpec with Matchers with EitherValues {

  "The play-json module" should "write arbitrary bodies given a Format" in {
    implicitly[Format[Outer]]

    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = basicRequest.body(asJson(body))

    extractBody(req) shouldBe expected
  }

  it should "read arbitrary bodies" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    RunResponseAs(responseAs)(body) shouldBe Right(expected)
  }

  it should "decode None from empty body" in {
    import OptionReads._

    val responseAs = asJson[Option[Inner]]

    RunResponseAs(responseAs)("") shouldBe Right(None)
  }

  it should "decode Left(None) from empty body" in {
    import OptionReads._
    import EitherReads._

    val responseAs = asJson[Either[Option[Inner], Outer]]

    RunResponseAs(responseAs)("") shouldBe Right(Left(None))
  }

  it should "decode Right(None) from empty body" in {
    import OptionReads._
    import EitherReads._

    val responseAs = asJson[Either[Outer, Option[Inner]]]

    RunResponseAs(responseAs)("") shouldBe Right(Right(None))
  }

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]

    RunResponseAs(responseAs)("") should matchPattern { case Left(DeserializationException("", _, _)) =>
    }
  }

  it should "fail to read invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    RunResponseAs(responseAs)(body) should matchPattern { case Left(DeserializationException(`body`, _, _)) =>
    }
  }

  it should "read and write back to the same thing" in {
    val outer = Outer(Inner(42, true, "horses"), "cats")

    val encoded = extractBody(basicRequest.body(asJson(outer)))
    val decoded = RunResponseAs(asJson[Outer])(encoded)

    decoded.value shouldBe outer
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

  it should "serialize from JsObject" in {
    val fields: Seq[(String, JsValue)] =
      Seq[(String, JsValue)](("location", JsString("hometown")), ("bio", JsString("Scala programmer")))
    val json: JsObject = JsObject(fields)
    val request: Request[Either[String, String]] = basicRequest.get(Uri("http://example.org")).body(asJson(json))

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

  case class Inner(a: Int, b: Boolean, c: String)

  object Inner {
    implicit val innerReads: Reads[Inner] = Json.reads[Inner]
    implicit val innerWrites: OWrites[Inner] = Json.writes[Inner]
  }

  case class Outer(foo: Inner, bar: String)

  object Outer {
    implicit val outerReads: Reads[Outer] = Json.reads[Outer]
    implicit val outerWrites: OWrites[Outer] = Json.writes[Outer]
  }

  object OptionReads {
    implicit def optionReads[R: Reads]: Reads[Option[R]] =
      new Reads[Option[R]] {
        override def reads(json: JsValue): JsResult[Option[R]] = json.validateOpt[R]
      }
  }

  object EitherReads {
    implicit def eitherReads[L: Reads, R: Reads]: Reads[Either[L, R]] =
      implicitly[Reads[L]].map[Either[L, R]](Left(_)).orElse(implicitly[Reads[R]].map(Right(_)))
  }

  def extractBody[T](request: PartialRequest[T]): String =
    request.body match {
      case StringBody(body, "utf-8", MediaType.ApplicationJson) =>
        body
      case wrongBody =>
        fail(s"Request body does not serialize to correct StringBody: $wrongBody")
    }
}
