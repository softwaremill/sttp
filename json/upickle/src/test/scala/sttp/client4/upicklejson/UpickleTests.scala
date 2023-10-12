package sttp.client4.upicklejson

import org.scalatest._
import sttp.client4.internal._
import sttp.client4._
import sttp.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ujson.Obj

class UpickleTests extends AnyFlatSpec with Matchers with EitherValues {
  "The upickle module" should "encode arbitrary bodies given an encoder" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = basicRequest.body(body)

    extractBody(req) shouldBe expected
  }

  it should "decode arbitrary bodies given a decoder" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body).right.value shouldBe expected
  }

  it should "decode None from empty array body" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val responseAs = asJson[Option[Inner]]

    runJsonResponseAs(responseAs)("[]").right.value shouldBe None
  }

  it should "decode Left(None) from upickle notation" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val responseAs = asJson[Either[Option[Inner], Outer]]

    runJsonResponseAs(responseAs)("[0,[]]").right.value shouldBe Left(None)
  }

  it should "decode Right(None) from upickle notation" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val responseAs = asJson[Either[Outer, Option[Inner]]]

    runJsonResponseAs(responseAs)("[1,[]]").right.value shouldBe Right(None)
  }

  it should "fail to decode from empty input" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val responseAs = asJson[Inner]

    runJsonResponseAs(responseAs)("").left.value should matchPattern { case DeserializationException(_, _) => }
  }

  it should "fail to decode invalid json" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val body = """not valid json"""

    val responseAs = asJson[Outer]

    val Left(DeserializationException(original, _)) = runJsonResponseAs(responseAs)(body)
    original shouldBe body
  }

  it should "encode and decode back to the same thing" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val outer = Outer(Inner(42, true, "horses"), "cats")

    val encoded = extractBody(basicRequest.body(outer))
    val decoded = runJsonResponseAs(asJson[Outer])(encoded)

    decoded.right.value shouldBe outer
  }

  it should "set the content type" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.body(body)

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some(MediaType.ApplicationJson.copy(charset = Some(Utf8)).toString)
  }

  it should "only set the content type if it was not set earlier" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.contentType("horses/cats").body(body)

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some("horses/cats")
  }

  it should "serialize ujson.Obj using implicit upickleBodySerializer" in {
    import UsingDefaultReaderWriters._
    import sttp.client4.upicklejson.default._

    val json: Obj = ujson.Obj(
      "location" -> "hometown",
      "bio" -> "Scala programmer"
    )
    val request: Request[Either[String, String]] = basicRequest.get(Uri("http://example.org")).body(json)

    val actualBody: String = request.body.show
    val actualContentType: Option[String] = request.contentType

    val expectedBody: String = "string: {\"location\":\"hometown\",\"bio\":\"Scala programmer\"}"
    val expectedContentType: Option[String] = Some("application/json; charset=utf-8")

    actualBody should be(expectedBody)
    actualContentType should be(expectedContentType)
  }

  it should "encode using a non-default reader/writer" in {
    import UsingLegacyReaderWriters._
    object legacyUpickle extends SttpUpickleApi {
      override val upickleApi: upickle.legacy.type = upickle.legacy
    }
    import legacyUpickle._

    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = basicRequest.body(body)

    extractBody(req) shouldBe expected
  }

  case class Inner(a: Int, b: Boolean, c: String)
  case class Outer(foo: Inner, bar: String)

  object UsingDefaultReaderWriters {
    import upickle.default._
    implicit val reader: ReadWriter[Inner] = macroRW[Inner]
    implicit val readWriter: ReadWriter[Outer] = macroRW[Outer]
  }

  object UsingLegacyReaderWriters {
    import upickle.legacy._
    implicit val reader: ReadWriter[Inner] = macroRW[Inner]
    implicit val readWriter: ReadWriter[Outer] = macroRW[Outer]
  }

  def extractBody[T](request: PartialRequest[T]): String =
    request.body match {
      case StringBody(body, "utf-8", MediaType.ApplicationJson) =>
        body
      case wrongBody =>
        fail(s"Request body does not serialize to correct StringBody: $wrongBody")
    }

  def runJsonResponseAs[A](responseAs: ResponseAs[A]): String => A =
    responseAs.delegate match {
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
