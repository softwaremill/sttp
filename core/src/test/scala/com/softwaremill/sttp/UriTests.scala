package com.softwaremill.sttp

import java.net.URI

import com.softwaremill.sttp.Uri.{
  QueryFragment,
  QueryFragmentEncoding,
  UserInfo
}
import org.scalatest.{FunSuite, Matchers}

class UriTests extends FunSuite with Matchers {

  val QF = QueryFragment

  val wholeUriTestData = List(
    Uri("http", None, "example.com", None, Nil, Nil, None) -> "http://example.com",
    Uri("https",
        None,
        "sub.example.com",
        Some(8080),
        List("a", "b", "xyz"),
        List(QF.KeyValue("p1", "v1"), QF.KeyValue("p2", "v2")),
        Some("f")) ->
      "https://sub.example.com:8080/a/b/xyz?p1=v1&p2=v2#f",
    Uri("http",
        None,
        "example.com",
        None,
        List(""),
        List(QF.KeyValue("p", "v"), QF.KeyValue("p", "v")),
        None) -> "http://example.com/?p=v&p=v",
    Uri("http",
        None,
        "exa mple.com",
        None,
        List("a b", "z", "ą:ę"),
        List(QF.KeyValue("p:1", "v&v"), QF.KeyValue("p2", "v v")),
        None) ->
      "http://exa%20mple.com/a%20b/z/%C4%85:%C4%99?p:1=v%26v&p2=v+v",
    Uri("http",
        Some(UserInfo("us&e/r", Some("pa ss"))),
        "example.com",
        None,
        Nil,
        Nil,
        None) ->
      "http://us&e%2Fr:pa%20ss@example.com",
    Uri("http", None, "example.com", None, Nil, Nil, Some("f:g/h i")) ->
      "http://example.com#f:g/h%20i",
    Uri("http", None, "example.com", None, List("key=value"), Nil, None) ->
      "http://example.com/key=value",
    Uri("2001:db8::ff00:42:8329", 8080) -> "http://[2001:db8::ff00:42:8329]:8080"
  )

  for {
    (uri, expected) <- wholeUriTestData
  } {
    test(s"$uri should serialize to $expected") {
      uri.toString should be(expected)
    }
  }

  val testUri = Uri("http", None, "example.com", None, Nil, Nil, None)

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

  val queryFragmentsTestData = List(
    List(QF.KeyValue("k1", "v1"),
         QF.KeyValue("k2", "v2"),
         QF.KeyValue("k3", "v3"),
         QF.KeyValue("k4", "v4")) -> "k1=v1&k2=v2&k3=v3&k4=v4",
    List(QF.KeyValue("k1", "v1"),
         QF.KeyValue("k2", "v2"),
         QF.Plain("-abc-"),
         QF.KeyValue("k3", "v3"),
         QF.KeyValue("k4", "v4")) -> "k1=v1&k2=v2-abc-k3=v3&k4=v4",
    List(QF.KeyValue("k1", "v1"), QF.Plain("&abc&"), QF.KeyValue("k2", "v2")) -> "k1=v1%26abc%26k2=v2",
    List(
      QF.KeyValue("k1", "v1"),
      QF.Plain("&abc&", encoding = QueryFragmentEncoding.Relaxed)) -> "k1=v1&abc&",
    List(QF.KeyValue("k1&", "v1&", keyEncoding = QueryFragmentEncoding.Relaxed)) -> "k1&=v1%26",
    List(QF.KeyValue(
      "k1&",
      "v1&",
      valueEncoding = QueryFragmentEncoding.Relaxed)) -> "k1%26=v1&",
    List(QF.Plain("ą/ę&+;?", encoding = QueryFragmentEncoding.Relaxed)) -> "%C4%85/%C4%99&+;?",
    List(QF.KeyValue("k", "v1,v2", valueEncoding = QueryFragmentEncoding.All)) -> "k=v1%2Cv2",
    List(QF.KeyValue("k", "v1,v2")) -> "k=v1,v2",
    List(QF.KeyValue("k", "+1234")) -> "k=%2B1234"
  )

  for {
    (fragments, expected) <- queryFragmentsTestData
  } {
    test(s"$fragments should serialize to$expected") {
      testUri.copy(queryFragments = fragments).toString should endWith(expected)
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
      Uri(host).toString should be(s"$expected")
    }
  }

  test("should convert from java URI") {
    val uriAsString = "https://sub.example.com:8080/a/b/xyz?p1=v1&p2=v2#f"
    Uri(URI.create(uriAsString)).toString should be(uriAsString)
  }

  test("should convert to java URI") {
    val uriAsString = "https://sub.example.com:8080/a/b/xyz?p1=v1&p2=v2#f"
    uri"$uriAsString".toJavaUri.toString should be(uriAsString)
  }
}
