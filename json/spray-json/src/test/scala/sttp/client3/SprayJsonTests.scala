package sttp.client3

import org.scalatest.EitherValues
import spray.json.DefaultJsonProtocol._
import spray.json.JsonParser.ParsingException
import spray.json.{DeserializationException => _, _}
import sttp.client3.SprayJsonTests._
import sttp.client3.internal.Utf8
import sttp.client3.sprayJson._
import sttp.model.{StatusCode, _}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SprayJsonTests extends AnyFlatSpec with Matchers with EitherValues {
  behavior of "The spray-json module"

  it should "encode arbitrary json bodies" in {
    val body = Outer(Inner(42, true, "horses"), "cats")

    val req = basicRequest.body(body)

    extractBody(req) should include(""""foo":{"a":42,"b":true,"c":"horses"}""")
    extractBody(req) should include(""""bar":"cats"""")
  }

  it should "decode arbitrary bodies" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body) shouldBe Right(expected)
  }

  it should "decode None from empty body" in {
    val responseAs = asJson[Option[Inner]]

    runJsonResponseAs(responseAs)("") shouldBe Right(None)
  }

  it should "decode Left(None) from empty body" in {
    val responseAs = asJson[Either[Option[Inner], Outer]]

    runJsonResponseAs(responseAs)("") shouldBe Right(Left(None))
  }

  it should "decode Right(None) from empty body" in {
    val responseAs = asJson[Either[Outer, Option[Inner]]]

    runJsonResponseAs(responseAs)("") shouldBe Right(Right(None))
  }

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]

    runJsonResponseAs(responseAs)("") should matchPattern {
      case Left(DeserializationException(_, _: ParsingException)) =>
    }
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body) should matchPattern {
      case Left(DeserializationException(_, _: ParsingException)) =>
    }
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.body(body)

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some(MediaType.ApplicationJson.copy(charset = Some(Utf8)).toString)
  }

  def extractBody[A[_], B, C](request: RequestT[A, B, C]): String =
    request.body match {
      case StringBody(body, "utf-8", MediaType.ApplicationJson) =>
        body
      case wrongBody =>
        fail(s"Request body does not serialize to correct StringBody: $wrongBody")
    }

  def runJsonResponseAs[A](responseAs: ResponseAs[A, Nothing]): String => A =
    responseAs match {
      case responseAs: MappedResponseAs[_, A, Nothing] =>
        responseAs.raw match {
          case ResponseAsByteArray =>
            s => responseAs.g(s.getBytes(Utf8), ResponseMetadata(StatusCode.Ok, "", Nil))
          case _ =>
            fail("MappedResponseAs does not wrap a ResponseAsByteArray")
        }
      case _ => fail("ResponseAs is not a MappedResponseAs")
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
