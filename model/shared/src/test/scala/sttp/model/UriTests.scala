package sttp.model

import java.net.URI

import Uri._
import org.scalatest.TryValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UriTests extends AnyFunSuite with Matchers with TryValues {

  val HS = HostSegment
  val PS = PathSegment
  val QS = QuerySegment

  val wholeUriTestData = List(
    Uri.unsafeApply("http", None, "example.com", None, Nil, Nil, None) -> "http://example.com",
    Uri.unsafeApply(
      "https",
      None,
      "sub.example.com",
      Some(8080),
      List("a", "b", "xyz"),
      List(QS.KeyValue("p1", "v1"), QS.KeyValue("p2", "v2")),
      Some("f")
    ) ->
      "https://sub.example.com:8080/a/b/xyz?p1=v1&p2=v2#f",
    Uri.unsafeApply(
      "http",
      None,
      "example.com",
      None,
      List(""),
      List(QS.KeyValue("p", "v"), QS.KeyValue("p", "v")),
      None
    ) -> "http://example.com/?p=v&p=v",
    Uri.unsafeApply(
      "http",
      None,
      "exa mple.com",
      None,
      List("a b", "z", "ą:ę"),
      List(QS.KeyValue("p:1", "v&v"), QS.KeyValue("p2", "v v")),
      None
    ) ->
      "http://exa%20mple.com/a%20b/z/%C4%85:%C4%99?p:1=v%26v&p2=v+v",
    Uri.unsafeApply("http", Some(UserInfo("us&e/r", Some("pa ss"))), "example.com", None, Nil, Nil, None) ->
      "http://us&e%2Fr:pa%20ss@example.com",
    Uri.unsafeApply("http", None, "example.com", None, Nil, Nil, Some("f:g/h i")) ->
      "http://example.com#f:g/h%20i",
    Uri.unsafeApply("http", None, "example.com", None, List("key=value"), Nil, None) ->
      "http://example.com/key=value",
    Uri.unsafeApply("2001:db8::ff00:42:8329", 8080) -> "http://[2001:db8::ff00:42:8329]:8080",
    Uri.unsafeApply("http", None, HS("example.com"), None, List(Segment("a b", identity)), Nil, None) -> "http://example.com/a b"
  )

  for {
    (uri, expected) <- wholeUriTestData
  } {
    test(s"$uri should serialize to $expected") {
      uri.toString should be(expected)
    }
  }

  val testUri = Uri.unsafeApply("http", None, "example.com", None, Nil, Nil, None)

  val pathTestData = List(
    "a/b/c" -> List("a", "b", "c"),
    "/a/b/c" -> List("a", "b", "c"),
    "/" -> List(""),
    "" -> List("")
  )

  for {
    (path, expected) <- pathTestData
  } {
    test(s"$path should parse as $expected") {
      testUri.path(path).path.toList should be(expected)
    }
  }

  val querySegmentsTestData = List(
    List(QS.KeyValue("k1", "v1"), QS.KeyValue("k2", "v2"), QS.KeyValue("k3", "v3"), QS.KeyValue("k4", "v4")) -> "k1=v1&k2=v2&k3=v3&k4=v4",
    List(
      QS.KeyValue("k1", "v1"),
      QS.KeyValue("k2", "v2"),
      QS.Plain("-abc-"),
      QS.KeyValue("k3", "v3"),
      QS.KeyValue("k4", "v4")
    ) -> "k1=v1&k2=v2-abc-k3=v3&k4=v4",
    List(QS.KeyValue("k1", "v1"), QS.Plain("&abc&"), QS.KeyValue("k2", "v2")) -> "k1=v1%26abc%26k2=v2",
    List(QS.KeyValue("k1", "v1"), QS.Plain("&abc&", encoding = QuerySegmentEncoding.Relaxed)) -> "k1=v1&abc&",
    List(QS.KeyValue("k1&", "v1&", keyEncoding = QuerySegmentEncoding.Relaxed)) -> "k1&=v1%26",
    List(QS.KeyValue("k1&", "v1&", valueEncoding = QuerySegmentEncoding.Relaxed)) -> "k1%26=v1&",
    List(QS.Plain("ą/ę&+;?", encoding = QuerySegmentEncoding.Relaxed)) -> "%C4%85/%C4%99&+;?",
    List(QS.KeyValue("k", "v1,v2", valueEncoding = QuerySegmentEncoding.All)) -> "k=v1%2Cv2",
    List(QS.KeyValue("k", "v1,v2")) -> "k=v1,v2",
    List(QS.KeyValue("k", "+1234")) -> "k=%2B1234",
    List(QS.KeyValue("k", "[]")) -> "k=%5B%5D",
    List(QS.KeyValue("k", "[]", valueEncoding = QuerySegmentEncoding.RelaxedWithBrackets)) -> "k=[]"
  )

  for {
    (segments, expected) <- querySegmentsTestData
  } {
    test(s"$segments should serialize to$expected") {
      testUri.copy(querySegments = segments).toString should endWith(expected)
    }
  }

  val hostTestData = List(
    "www.mikołak.net" -> "http://www.xn--mikoak-6db.net",
    "192.168.1.0" -> "http://192.168.1.0",
    "::1" -> "http://[::1]",
    "2001:db8::ff00:42:8329" -> "http://[2001:db8::ff00:42:8329]",
    "2001:0db8:0000:0000:0000:ff00:0042:8329" -> "http://[2001:0db8:0000:0000:0000:ff00:0042:8329]"
  )

  for {
    (host, expected) <- hostTestData
  } {
    test(s"host $host should serialize to $expected") {
      Uri.unsafeApply(host).toString should be(s"$expected")
    }
  }

  test("should convert from java URI") {
    val uriAsString = "https://sub.example.com:8080/a/b/xyz?p1=v1&p2=v2#f"
    Uri(URI.create(uriAsString)).toString should be(uriAsString)
  }

  test("should parse raw string") {
    val uriAsString = "https://sub.example.com:8080/a/b/xyz?p1=v1&p2=v2#f"
    Uri.parse(uriAsString).right.map(_.toString) shouldBe Right(uriAsString)
    val badString = "xyz://foobar:80:37/?&?"
    Uri.parse(badString).isLeft shouldBe true
  }

  test("should convert to java URI") {
    val uriAsString = "https://sub.example.com:8080/a/b/xyz?p1=v1&p2=v2#f"
    uri"$uriAsString".toJavaUri.toString should be(uriAsString)
  }

  test("should have multi params") {
    val uriAsString = "https://sub.example.com:8080?p1=v1&p2=v2&p1=v3&p2=v4"
    val expectedParams = Map("p1" -> List("v1", "v3"), "p2" -> List("v2", "v4"))
    uri"$uriAsString".multiParams.toMultiMap should be(expectedParams)
  }

  test("should have no multi params") {
    val uriAsString = "https://sub.example.com:8080"
    uri"$uriAsString".multiParams.toMultiMap should be(Map())
  }

  test("should have empty multi params") {
    val uriAsString = "https://sub.example.com:8080?p1&p2"
    uri"$uriAsString".multiParams.toMultiMap should be(Map())
  }

  test("should have multi params with empty string values") {
    val uriAsString = "https://sub.example.com:8080?p1=&p2="
    uri"$uriAsString".multiParams.toMultiMap should be(Map("p1" -> List(""), "p2" -> List("")))
  }

  test("should have multi params with only values") {
    val uriAsString = "https://sub.example.com:8080?=v1&=v2"
    uri"$uriAsString".multiParams.toMultiMap should be(Map("" -> List("v1", "v2")))
  }

  val validationTestData = List(
    (() => Uri.unsafeApply("")) -> "host cannot be empty",
    (() => Uri.unsafeApply("h ttp", "example.org")) -> "scheme"
  )

  for {
    (createUri, expectedException) <- validationTestData
  } {
    test(s"""should validate and throw "$expectedException" if not valid""") {
      val caught = intercept[IllegalArgumentException] {
        createUri()
      }

      caught.getMessage.toLowerCase() should include(expectedException)
    }
  }
}
