package sttp.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MediaTypeTests extends AnyFlatSpec with Matchers {
  val parseMediaTypeData = List(
    "text/html" -> Right(MediaType.unsafeApply("text", "html")),
    "text/html; charset=UTF-8" -> Right(MediaType.unsafeApply("text", "html", Some("UTF-8"))),
    "text/plain;a=1;b=2;charset=utf-8;c=3" -> Right(MediaType.unsafeApply("text", "plain", Some("utf-8"))),
    "text/html;" -> Right(MediaType.unsafeApply("text", "html")),
    "multipart/form-data" -> Right(MediaType.unsafeApply("multipart", "form-data")),
    "application/atom+xml" -> Right(MediaType.unsafeApply("application", "atom+xml")),
    "te<t/plain" -> Left("""No subtype found for: "te<t/plain""""),
    "text/plain; a=1; b=" -> Left("""Parameter is not formatted correctly: "b=" for: "text/plain; a=1; b="""")
  )

  for ((mediaTypeValue, expectedResult) <- parseMediaTypeData) {
    it should s"parse or error: $mediaTypeValue" in {
      MediaType.parse(mediaTypeValue) shouldBe expectedResult
    }
  }

  val serializeMediaTypeData = List(
    MediaType.unsafeApply("text", "html") -> "text/html",
    MediaType.unsafeApply("text", "html", Some("utf-8")) -> "text/html; charset=utf-8",
    MediaType.unsafeApply("multipart", "form-data") -> "multipart/form-data",
    MediaType.unsafeApply("application", "atom+xml") -> "application/atom+xml"
  )

  for ((mediaType, expectedResult) <- serializeMediaTypeData) {
    it should s"serialize $mediaType to $expectedResult" in {
      mediaType.toString shouldBe expectedResult
    }
  }

  it should "validate media types" in {
    MediaType.safeApply("text", "p=lain") shouldBe 'left
    MediaType.safeApply("text", "plain", Some("UTF=8")) shouldBe 'left
    MediaType.safeApply("text", "plain") shouldBe Right(MediaType.TextPlain)
  }

  it should "throw exceptions on invalid media types" in {
    an[IllegalArgumentException] shouldBe thrownBy(MediaType.unsafeApply("te=xt", "plain"))
  }
}
