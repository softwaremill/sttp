package sttp.client3

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.internal.Rfc3986
import sttp.model.{Header, StatusCode, Uri}

class FollowRedirectsBackendTest extends AnyFunSuite with Matchers with EitherValues {
  val testData = List(
    ("/x/y/z", true),
    ("  /x2/y/z", true),
    ("/x?query=10", true),
    ("/foo%3F?token=xyz&url=http://minio:9000/a/b/c", true),
    ("http://server.com", false),
    ("https://server.com", false),
    ("  https://server2.com", false),
    ("HTTP://server.com", false),
    ("httpS://server.com", false),
    ("xyz://server.com", false),
    ("index.html", true)
  )

  for ((uri, isRelative) <- testData) {
    test(s"$uri should ${if (isRelative) "" else "not "}be relative") {
      FollowRedirectsBackend.isRelative(uri) shouldBe isRelative
    }
  }

  test("send should encode the redirect location with the provided encoder") {
    val pathEncoder: String => String = Rfc3986.encode(Rfc3986.PathSegment - '+' - '(' - ')' - ',')

    val url0 = uri"https://server.com/download"
    val url1Source = "https://elsewhere.com/A%2C%20File%20With%20Spaces%20.txt"

    val stub0 = HttpURLConnectionBackend.stub
      .whenRequestMatches(_.uri == url0)
      .thenRespond(Response[String]("", StatusCode.Found, "", Seq(Header.location(url1Source))))
      .whenRequestMatches(_.uri.toString() == url1Source)
      .thenRespond(Response.ok("All good!"))

    val redirectsBackend = new FollowRedirectsBackend(
      stub0,
      uriEncoder = (original: Uri) => {
        original.copy(pathSegments =
          Uri.AbsolutePath(original.pathSegments.segments.map(_.copy(encoding = pathEncoder)).toList)
        )
      }
    )

    val request = basicRequest.get(url0)
    val result = redirectsBackend.send(request)
    result.body.value shouldBe "All good!"
  }

}
