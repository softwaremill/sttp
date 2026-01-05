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
import org.scalatest.Checkpoints
import sttp.model.headers.Cookie
import sttp.model.HeaderNames

class FollowRedirectsBackendTest extends AnyFunSuite with Matchers with EitherValues with Checkpoints {
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

    val maxRedirects = 5
    val staticName = "session"

    val (idToUrl, urlToId) = {
      val x = (0 to maxRedirects).map(id => id -> uri"https://example.com/$id")
      (x.toMap, x.map { case (id, url) => url -> id }.toMap)
    }

    def response(number: Int) = ResponseStub
      .adjust(
        "",
        StatusCode.Found,
        Vector(
          Header.location(idToUrl.apply(number + 1)),
          Header.setCookie(CookieWithMeta(staticName, (number + 1).toString)),
          Header.setCookie(CookieWithMeta(s"$number-fuzz", ""))
        )
      )

    val checkpoint = new Checkpoint()

    val stub = BackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if (urlToId.contains(r.uri)) => {
          val id = urlToId.apply(r.uri)
          if (id < maxRedirects) {
            checkpoint(
              r.unsafeCookies.map(_.name) should contain(staticName)
            )
            response(urlToId.apply(r.uri))
          } else {
            ResponseStub.adjust("", StatusCode.Ok, Vector(Header.setCookie(CookieWithMeta(staticName, "final"))))
          }
        }
        case r => ResponseStub.adjust(s"No cookies found, or unxpected request $r")
      }

    val redirectsBackend =
      wrappers.FollowRedirectsBackend(stub, config = FollowRedirectsConfig(sensitiveHeaders = Set.empty))
    val result = basicRequest.redirectToGet(true).post(idToUrl.apply(0)).send(redirectsBackend)

    checkpoint(result.code shouldBe StatusCode.Ok)

    // typical solution needed to populate the next request (oldest to newest),
    // assuming map internals have not changed
    // adds oldest first which gets overriden by the last item on the list
    val cookies = result.history
      .flatMap(_.unsafeCookies)
      .appendedAll(result.unsafeCookies)
      .map(cookie => (cookie.name, cookie.domain) -> cookie)
      .toMap
      .values
      .toSeq
      .sortBy(_.name)

    checkpoint(result.unsafeCookies.sortBy(_.name) shouldEqual cookies)
    checkpoint.reportAll()
  }
}
