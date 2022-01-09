package sttp.client3.jsoniter

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.internal.Utf8

import sttp.model._
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._

class JsoniterJsonTests extends AnyFlatSpec with Matchers with EitherValues {

  "The jsoniter module" should "encode arbitrary bodies given an encoder" in {
    val body = Outer(Inner(42, true, "horses"), "cats")
    val expected = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val req = basicRequest.body(body)
    extractBody(req) shouldBe expected
  }

  it should "decode arbitrary bodies given a decoder" in {
    val body = """{"foo":{"a":42,"b":true,"c":"horses"},"bar":"cats"}"""
    val expected = Outer(Inner(42, true, "horses"), "cats")

    val responseAs = asJson[Outer]

    runJsonResponseAs(responseAs)(body).value shouldBe expected
  }

  it should "decode None from empty body" in {
    import Decoders._
    val responseAs = asJson[Option[Inner]]
    runJsonResponseAs(responseAs)("").value shouldBe None
  }

  it should "decode Left(None) from empty body" in {
    import Decoders._
    val responseAs = asJson[Either[Option[Inner], Outer]]
    runJsonResponseAs(responseAs)("").value shouldBe Left(None)
  }

  it should "decode Right(None) from empty body" in {
    import Decoders._
    val responseAs = asJson[Either[Outer, Option[Inner]]]

    runJsonResponseAs(responseAs)("").value shouldBe Right(None)
  }

  it should "fail to decode invalid json" in {
    val body = """not valid json"""

    val responseAs = asJson[Outer]

    val Left(DeserializationException(original, _)) = runJsonResponseAs(responseAs)(body)
    original shouldBe body
  }

  it should "fail to decode from empty input" in {
    val responseAs = asJson[Inner]
    runJsonResponseAs(responseAs)("").left.value should matchPattern { case DeserializationException("", _: Exception) =>
    }
  }

  it should "read what it writes" in {
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

  object Decoders {
    implicit def eitherOptionDecoder[L:JsonValueCodec: IsOption, R:JsonValueCodec: IsOption] : JsonValueCodec[Either[L, R]] = new JsonValueCodec[Either[L, R]] {

      val rightOption = implicitly[IsOption[R]].isOption
      val leftOption = implicitly[IsOption[L]].isOption

      val leftEnc = implicitly[JsonValueCodec[L]]
      val rightEnc = implicitly[JsonValueCodec[R]]

      val decoder = JsonCodecMaker.make[Either[L, R]]
      override def decodeValue(in: JsonReader, default: Either[L, R]): Either[L, R] = {
        try {
          in.setMark()
          if (rightOption || !leftOption) {
            Right(rightEnc.decodeValue(in, rightEnc.nullValue))
          } else  {
            Left(leftEnc.decodeValue(in, leftEnc.nullValue))
          }
        } catch {
          case e: JsonReaderException =>
            in.rollbackToMark()
            try {
              Right(rightEnc.decodeValue(in, rightEnc.nullValue))
            } catch {
              case e: JsonReaderException => Left(leftEnc.decodeValue(in, leftEnc.nullValue))
            }
        }

      }

      override def encodeValue(x: Either[L, R], out: JsonWriter): Unit = x match {
        case Left(value) => leftEnc.encodeValue(value, out)
        case Right(value) =>  rightEnc.encodeValue(value, out)
      }

      override def nullValue: Either[L, R] = null
    }

    implicit def optionDecoder[T:JsonValueCodec] : JsonValueCodec[Option[T]]= new JsonValueCodec[Option[T]] {
      val codec = implicitly[JsonValueCodec[T]]
      override def decodeValue(in: JsonReader, default: Option[T]): Option[T] = {
        if (
          in.isNextToken('n'.toByte)
            && in.isNextToken('u'.toByte)
            && in.isNextToken('l'.toByte)
            && in.isNextToken('l'.toByte)
        ) {
          None
        }
        else {
          in.rollbackToken()
          Some(codec.decodeValue(in, codec.nullValue))
        }
      }

      override def encodeValue(x: Option[T], out: JsonWriter): Unit = x.foreach(codec.encodeValue(_,out))

      override def nullValue: Option[T] = null
    }
  }
}

case class Inner(a: Int, b: Boolean, c: String)

object Inner {
  implicit val codec: JsonValueCodec[Inner] = JsonCodecMaker.make
}

case class Outer(foo: Inner, bar: String)

object Outer {
  implicit val codec: JsonValueCodec[Outer] = JsonCodecMaker.make
}
