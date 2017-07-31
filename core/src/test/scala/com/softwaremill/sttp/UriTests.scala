package com.softwaremill.sttp

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
      "http://exa%20mple.com/a%20b/z/%C4%85%3A%C4%99?p%3A1=v%26v&p2=v+v",
    Uri("http", Some("us&er:pa ss"), "example.com", None, Nil, Nil, None) ->
      "http://us%26er:pa%20ss@example.com",
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
    List(QF.KeyValue("k1", "v1"), QF.Plain("&abc&", relaxedEncoding = true)) -> "k1=v1&abc&",
    List(QF.KeyValue("k1?", "v1?", keyRelaxedEncoding = true)) -> "k1?=v1%3F",
    List(QF.KeyValue("k1?", "v1?", valueRelaxedEncoding = true)) -> "k1%3F=v1?",
    List(QF.Plain("ą/ę&+;?", relaxedEncoding = true)) -> "%C4%85/%C4%99&+;?"
  )

  for {
    (fragments, expected) <- queryFragmentsTestData
  } {
    test(s"$fragments should serialize to$expected") {
      testUri.copy(queryFragments = fragments).toString should endWith(expected)
    }
  }
}
