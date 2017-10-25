package com.softwaremill.sttp

import org.json4s.ParserUtil.ParseException
import org.scalatest._

import scala.language.higherKinds

class Json4sTests extends FlatSpec with Matchers with EitherValues {
  import json4s._
  import Json4sTests._

  "The json4s module" should "encode arbitrary json bodies" in {
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

    an[ParseException] should be thrownBy runJsonResponseAs(responseAs)(body)
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = sttp.body(body)

    val ct = req.headers.toMap.get("Content-Type")

    ct shouldBe Some(contentTypeWithEncoding(ApplicationJsonContentType, Utf8))
  }

  def extractBody[A[_], B, C](request: RequestT[A, B, C]): String =
    request.body match {
      case StringBody(body, "utf-8", Some(ApplicationJsonContentType)) =>
        body
      case wrongBody =>
        fail(
          s"Request body does not serialize to correct StringBody: $wrongBody")
    }

  def runJsonResponseAs[A](responseAs: ResponseAs[A, Nothing]): String => A =
    responseAs match {
      case responseAs: MappedResponseAs[_, A, Nothing] =>
        responseAs.raw match {
          case ResponseAsString("utf-8") =>
            responseAs.g
          case ResponseAsString(encoding) =>
            fail(
              s"MappedResponseAs wraps a ResponseAsString with wrong encoding: $encoding")
          case _ =>
            fail("MappedResponseAs does not wrap a ResponseAsString")
        }
      case _ => fail("ResponseAs is not a MappedResponseAs")
    }
}

object Json4sTests {
  case class Inner(a: Int, b: Boolean, c: String)
  case class Outer(foo: Inner, bar: String)
}
