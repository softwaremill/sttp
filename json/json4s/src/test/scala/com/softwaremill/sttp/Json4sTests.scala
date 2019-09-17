package com.softwaremill.sttp

import com.softwaremill.sttp.internal._
import org.json4s.MappingException
import org.json4s.ParserUtil.ParseException
import org.json4s.native
import org.scalatest._

import scala.language.higherKinds

class Json4sTests extends FlatSpec with Matchers with EitherValues {
  implicit val serialization = native.Serialization
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
      case Left(DeserializationError(_, _: MappingException)) =>
    }
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body) should matchPattern {
      case Left(DeserializationError(_, _: ParseException)) =>
    }
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = sttp.body(body)

    val ct = req.headers.toMap.get("Content-Type")

    ct shouldBe Some(contentTypeWithCharset(MediaTypes.Json, Utf8))
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
          case ResponseAsByteArray =>
            s => responseAs.g(s.getBytes(Utf8), ResponseMetadata(Nil, 200, ""))
          case _ =>
            fail("MappedResponseAs does not wrap a ResponseAsByteArray")
        }
      case _ => fail("ResponseAs is not a MappedResponseAs")
    }
}

object Json4sTests {
  case class Inner(a: Int, b: Boolean, c: String)
  case class Outer(foo: Inner, bar: String)
}
