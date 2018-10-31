package com.softwaremill.sttp

import com.softwaremill.sttp.internal._
import com.softwaremill.sttp.playJson._
import play.api.libs.json._
import org.scalatest._

class PlayJsonTests extends FlatSpec with Matchers with EitherValues {

  "The play-json module" should "write arbitrary bodies given a Format" in {
    implicitly[Format[Outer]]

    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = sttp.body(body)

    extractBody(req) shouldBe expected
  }

  it should "read arbitrary bodies" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body).right.value shouldBe expected
  }

  it should "decode None from empty body" in {
    import OptionReads._

    val responseAs = asJson[Option[Inner]]

    runJsonResponseAs(responseAs)("").right.value shouldBe None
  }

  it should "decode Left(None) from empty body" in {
    import OptionReads._
    import EitherReads._

    val responseAs = asJson[Either[Option[Inner], Outer]]

    runJsonResponseAs(responseAs)("").right.value shouldBe Left(None)
  }

  it should "decode Right(None) from empty body" in {
    import OptionReads._
    import EitherReads._

    val responseAs = asJson[Either[Outer, Option[Inner]]]

    runJsonResponseAs(responseAs)("").right.value shouldBe Right(None)
  }

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]

    val result = runJsonResponseAs(responseAs)("").left.value
    result.original shouldBe ""
  }

  it should "fail to read invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    val result = runJsonResponseAs(responseAs)(body).left.value
    result.original shouldBe body
  }

  it should "read and write back to the same thing" in {
    val outer = Outer(Inner(42, true, "horses"), "cats")

    val encoded = extractBody(sttp.body(outer))
    val decoded = runJsonResponseAs(asJson[Outer])(encoded)

    decoded.right.value shouldBe outer
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = sttp.body(body)

    val ct = req.headers.toMap.get("Content-Type")

    ct shouldBe Some(contentTypeWithEncoding(MediaTypes.Json, Utf8))
  }

  it should "only set the content type if it was not set earlier" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = sttp.contentType("horses/cats").body(body)

    val ct = req.headers.toMap.get("Content-Type")

    ct shouldBe Some("horses/cats")
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
    implicit def reads[R: Reads]: Reads[Option[R]] = _.validateOpt[R]
  }

  object EitherReads {
    implicit def reads[L: Reads, R: Reads]: Reads[Either[L, R]] =
      implicitly[Reads[L]].map[Either[L, R]](Left(_)).orElse(implicitly[Reads[R]].map(Right(_)))
  }

  def extractBody[A[_], B, C](request: RequestT[A, B, C]): String =
    request.body match {
      case StringBody(body, "utf-8", Some(MediaTypes.Json)) =>
        body
      case wrongBody =>
        fail(s"Request body does not serialize to correct StringBody: $wrongBody")
    }

  def runJsonResponseAs[A](responseAs: ResponseAs[A, Nothing]): String => A =
    responseAs match {
      case responseAs: MappedResponseAs[_, A, Nothing] =>
        responseAs.raw match {
          case ResponseAsString("utf-8") =>
            responseAs.g
          case ResponseAsString(encoding) =>
            fail(s"MappedResponseAs wraps a ResponseAsString with wrong encoding: $encoding")
          case _ =>
            fail("MappedResponseAs does not wrap a ResponseAsString")
        }
      case _ => fail("ResponseAs is not a MappedResponseAs")
    }
}
