package com.softwaremill.sttp

import com.softwaremill.sttp.SprayJsonTests._
import com.softwaremill.sttp.internal.{Utf8, contentTypeWithEncoding}
import com.softwaremill.sttp.json.sprayJson._
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import spray.json.DefaultJsonProtocol._
import spray.json.JsonParser.ParsingException
import spray.json._

class SprayJsonTests extends FlatSpec with Matchers with EitherValues {
  behavior of "The spray-json module"

  it should "encode arbitrary json bodies" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = sttp.body(body)

    extractBody(req) shouldBe expected
  }

  it should "decode arbitrary bodies" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body) shouldBe expected
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    an[ParsingException] should be thrownBy runJsonResponseAs(responseAs)(body)
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = sttp.body(body)

    val ct = req.headers.toMap.get("Content-Type")

    ct shouldBe Some(contentTypeWithEncoding(MediaTypes.Json, Utf8))
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
