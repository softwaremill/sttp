package sttp.client4

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.{BackendStub, ResponseStub}
import sttp.client4.wrappers.{CookieStorage, FollowRedirectsBackend, FollowRedirectsConfig}
import sttp.model.internal.Rfc3986
import sttp.model.{Header, HeaderNames, StatusCode, Uri}

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

  // a redirect chain example.com/0 -> /1 -> ... -> /n, where each hop sets a cookie `c<id>`; records the cookies
  // (name=value) seen in the `Cookie` header of each request, by target id
  private def redirectChainSettingCookies(n: Int): (SyncBackend, collection.Map[Int, Set[String]]) = {
    def url(id: Int) = uri"https://example.com/$id"
    val seen = scala.collection.mutable.Map[Int, Set[String]]()
    val stub = BackendStub.synchronous.whenRequestMatchesPartial {
      case r if r.uri.host.contains("example.com") =>
        val id = r.uri.path.last.toInt
        seen(id) = r.header(HeaderNames.Cookie).map(_.split("; ").toSet).getOrElse(Set.empty)
        if (id < n)
          ResponseStub.adjust(
            "",
            StatusCode.Found,
            Vector(Header.location(url(id + 1)), Header(HeaderNames.SetCookie, s"c$id=$id"))
          )
        else ResponseStub.adjust("done", StatusCode.Ok)
    }
    (FollowRedirectsBackend(stub), seen)
  }

  test("should send cookies set during a redirect chain to subsequent requests when a cookie storage is attached") {
    val (backend, seen) = redirectChainSettingCookies(3)

    val result = basicRequest.get(uri"https://example.com/0").cookieStorage(CookieStorage.empty).send(backend)

    result.code shouldBe StatusCode.Ok
    seen(0) shouldBe empty // nothing set yet
    seen(1) shouldBe Set("c0=0")
    seen(2) shouldBe Set("c0=0", "c1=1")
    seen(3) shouldBe Set("c0=0", "c1=1", "c2=2")
  }

  test("should not carry cookies across redirects when no cookie storage is attached") {
    val (backend, seen) = redirectChainSettingCookies(3)

    val result = basicRequest.get(uri"https://example.com/0").send(backend)

    result.code shouldBe StatusCode.Ok
    seen.values.foreach(_ shouldBe empty)
  }

}
