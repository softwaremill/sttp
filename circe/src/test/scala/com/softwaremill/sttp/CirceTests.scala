package com.softwaremill.sttp

import com.softwaremill.sttp.model._
import io.circe._
import org.scalatest._

import scala.language.higherKinds

class CirceTests extends FlatSpec with Matchers with EitherValues {
  import circe._

  "The circe module" should "encode arbitrary bodies given an encoder" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = sttp.jsonBody(body)

    extractBody(req) shouldBe expected
  }

  it should "decode arbitrary bodies given a decoder" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body).right.value shouldBe expected
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body).left.value shouldBe an[io.circe.Error]
  }

  it should "should encode and decode back to the same thing" in {
    val outer = Outer(Inner(42, true, "horses"), "cats")

    val encoded = extractBody(sttp.jsonBody(outer))
    val decoded = runJsonResponseAs(asJson[Outer])(encoded)

    decoded.right.value shouldBe outer
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = sttp.jsonBody(body)

    val ct = req.headers.toMap.get("Content-Type")

    ct shouldBe Some("application/json")
  }

  it should "only set the content type if it was not set earlier" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = sttp.contentType("horses/cats").jsonBody(body)

    val ct = req.headers.toMap.get("Content-Type")

    ct shouldBe Some("horses/cats")
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

  def extractBody[A[_], B, C](request: RequestT[A, B, C]): String =
    request.body match {
      case SerializableBody(serializer, body) =>
        serializer(body) match {
          case StringBody(body, "utf-8") =>
            body
          case StringBody(_, encoding) =>
            fail(
              s"Request body serializes to StringBody with wrong encoding: $encoding")
          case _ =>
            fail("Request body does not serialize to StringBody")
        }
      case _ =>
        fail("Request body is not SerializableBody")
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
