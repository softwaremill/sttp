package sttp.client4

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.{BackendStub, ResponseStub}
import sttp.client4.wrappers.{FollowRedirectsBackend, FollowRedirectsConfig}
import sttp.model.internal.Rfc3986
import sttp.model.{Header, StatusCode, Uri}
import sttp.model.headers.CookieWithMeta
import sttp.model.Method

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

  for ((uri, isRelative) <- testData)
    test(s"$uri should ${if (isRelative) "" else "not "}be relative") {
      FollowRedirectsBackend.isRelative(uri) shouldBe isRelative
    }

  test("send should encode the redirect location with the provided encoder") {
    val pathEncoder: String => String = Rfc3986.encode(Rfc3986.PathSegment - '+' - '(' - ')' - ',')

    val url0 = uri"https://server.com/download"
    val url1Source = "https://elsewhere.com/A%2C%20File%20With%20Spaces%20.txt"

    val response0 = ResponseStub.adjust("", StatusCode.Found, Vector(Header.location(url1Source)))
    val response1 = ResponseStub.adjust("All good!")

    val stub0 = BackendStub.synchronous
      .whenRequestMatches(_.uri == url0)
      .thenRespond(response0)
      .whenRequestMatches(_.uri.toString() == url1Source)
      .thenRespond(response1)

    val transformUri = (original: Uri) =>
      original.copy(pathSegments =
        Uri.AbsolutePath(original.pathSegments.segments.map(_.copy(encoding = pathEncoder)).toList)
      )
    val redirectsBackend = wrappers.FollowRedirectsBackend(stub0, FollowRedirectsConfig(transformUri = transformUri))

    val result = basicRequest.get(url0).send(redirectsBackend)
    result.body.value shouldBe "All good!"
  }

  test("should pass cookies during redirections") {

    val url0 = uri"https://example.com/0"
    val url1 = uri"https://example.com/1"

    val response0 = ResponseStub
      .adjust(
        "",
        StatusCode.Found,
        Vector(
          Header.location(url1),
          Header.setCookie(CookieWithMeta("session", "one"))
        )
      )

    val stub0 = BackendStub.synchronous
      .whenRequestMatches { r => r.uri == url0 && r.method == Method.POST }
      .thenRespond(response0)
      .whenRequestMatches { r => r.uri == url1 && r.method == Method.GET && r.unsafeCookies.isEmpty }
      .thenRespondUnauthorized()
      .whenRequestMatches { r =>
        r.uri == url1 && r.method == Method.GET && r.unsafeCookies.map(_.name).contains("session")
      }
      .thenRespondOk()
      .whenAnyRequest
      .thenRespondNotFound()

    val redirectsBackend =
      wrappers.FollowRedirectsBackend(stub0, config = FollowRedirectsConfig(sensitiveHeaders = Set.empty))
    val result = basicRequest.redirectToGet(true).post(url0).send(redirectsBackend)

    result.unsafeCookies.size shouldEqual 1
    result.code shouldBe StatusCode.Ok
  }
}
