package sttp.client

import sttp.client.internal._
import sttp.client.playJson._
import sttp.model._
import play.api.libs.json._
import org.scalatest._
import sttp.model.StatusCode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PlayJsonTests extends AnyFlatSpec with Matchers with EitherValues {

  "The play-json module" should "write arbitrary bodies given a Format" in {
    implicitly[Format[Outer]]

    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""

    val req = basicRequest.body(body)

    extractBody(req) shouldBe expected
  }

  it should "read arbitrary bodies" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body) shouldBe Right(expected)
  }

  it should "decode None from empty body" in {
    import OptionReads._

    val responseAs = asJson[Option[Inner]]

    runJsonResponseAs(responseAs)("") shouldBe Right(None)
  }

  it should "decode Left(None) from empty body" in {
    import OptionReads._
    import EitherReads._

    val responseAs = asJson[Either[Option[Inner], Outer]]

    runJsonResponseAs(responseAs)("") shouldBe Right(Left(None))
  }

  it should "decode Right(None) from empty body" in {
    import OptionReads._
    import EitherReads._

    val responseAs = asJson[Either[Outer, Option[Inner]]]

    runJsonResponseAs(responseAs)("") shouldBe Right(Right(None))
  }

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]

    runJsonResponseAs(responseAs)("") should matchPattern {
      case Left(DeserializationError("", _)) =>
    }
  }

  it should "fail to read invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body) should matchPattern {
      case Left(DeserializationError(`body`, _)) =>
    }
  }

  it should "read and write back to the same thing" in {
    val outer = Outer(Inner(42, true, "horses"), "cats")

    val encoded = extractBody(basicRequest.body(outer))
    val decoded = runJsonResponseAs(asJson[Outer])(encoded)

    decoded.right.value shouldBe outer
  }

  it should "set the content type" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.body(body)

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

    ct shouldBe Some(MediaType.ApplicationJson.copy(charset = Some(Utf8)).toString)
  }

  it should "only set the content type if it was not set earlier" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val req = basicRequest.contentType("horses/cats").body(body)

    val ct = req.headers.map(h => (h.name, h.value)).toMap.get("Content-Type")

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
    implicit def optionReads[R: Reads]: Reads[Option[R]] =
      new Reads[Option[R]] {
        override def reads(json: JsValue): JsResult[Option[R]] = json.validateOpt[R]
      }
  }

  object EitherReads {
    implicit def eitherReads[L: Reads, R: Reads]: Reads[Either[L, R]] =
      implicitly[Reads[L]].map[Either[L, R]](Left(_)).orElse(implicitly[Reads[R]].map(Right(_)))
  }

  def extractBody[A[_], B, C](request: RequestT[A, B, C]): String =
    request.body match {
      case StringBody(body, "utf-8", Some(MediaType.ApplicationJson)) =>
        body
      case wrongBody =>
        fail(s"Request body does not serialize to correct StringBody: $wrongBody")
    }

  def runJsonResponseAs[A](responseAs: ResponseAs[A, Nothing]): String => A =
    responseAs match {
      case responseAs: MappedResponseAs[_, A, Nothing] =>
        responseAs.raw match {
          case ResponseAsByteArray =>
            s => responseAs.g(s.getBytes(Utf8), ResponseMetadata(Nil, StatusCode.Ok, ""))
          case _ =>
            fail("MappedResponseAs does not wrap a ResponseAsByteArray")
        }
      case _ => fail("ResponseAs is not a MappedResponseAs")
    }
}
