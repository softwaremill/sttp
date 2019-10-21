package sttp.model

import org.scalatest.{FlatSpec, Matchers}

class MediaTypeTests extends FlatSpec with Matchers {
  val parseMediaTypeData = List(
    "text/html" -> Right(MediaType("text", "html")),
    "text/html; charset=UTF-8" -> Right(MediaType("text", "html", Some("UTF-8"))),
    "text/plain;a=1;b=2;charset=utf-8;c=3" -> Right(MediaType("text", "plain", Some("utf-8"))),
    "text/html;" -> Right(MediaType("text", "html")),
    "multipart/form-data" -> Right(MediaType("multipart", "form-data")),
    "application/atom+xml" -> Right(MediaType("application", "atom+xml")),
    "te<t/plain" -> Left("""No subtype found for: "te<t/plain""""),
    "text/plain; a=1; b=" -> Left("""Parameter is not formatted correctly: "b=" for: "text/plain; a=1; b="""")
  )

  for ((mediaTypeValue, expectedResult) <- parseMediaTypeData) {
    it should s"parse or error: $mediaTypeValue" in {
      MediaType.parse(mediaTypeValue) shouldBe expectedResult
    }
  }

  val serializeMediaTypeData = List(
    MediaType("text", "html") -> "text/html",
    MediaType("text", "html", Some("utf-8")) -> "text/html; charset=utf-8",
    MediaType("multipart", "form-data") -> "multipart/form-data",
    MediaType("application", "atom+xml") -> "application/atom+xml"
  )

  for ((mediaType, expectedResult) <- serializeMediaTypeData) {
    it should s"serialize $mediaType to $expectedResult" in {
      mediaType.toString shouldBe expectedResult
    }
  }
}
