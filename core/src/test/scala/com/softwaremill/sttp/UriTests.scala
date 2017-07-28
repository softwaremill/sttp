package com.softwaremill.sttp

import org.scalatest.{FunSuite, Matchers}

class UriTests extends FunSuite with Matchers {

  val QF = QueryFragment

  val wholeUriTestData = List(
    Uri("http", "example.com", None, Nil, Nil, None) -> "http://example.com",
    Uri("https",
        "sub.example.com",
        Some(8080),
        List("a", "b", "xyz"),
        List(QF.KeyValue("p1", "v1"), QF.KeyValue("p2", "v2")),
        Some("f")) ->
      "https://sub.example.com:8080/a/b/xyz?p1=v1&p2=v2#f",
    Uri("http",
        "example.com",
        None,
        List(""),
        List(QF.KeyValue("p", "v"), QF.KeyValue("p", "v")),
        None) -> "http://example.com/?p=v&p=v",
    Uri("http",
        "exa mple.com",
        None,
        List("a b", "z", "ą:ę"),
        List(QF.KeyValue("p:1", "v&v"), QF.KeyValue("p2", "v v")),
        None) ->
      "http://exa%20mple.com/a%20b/z/%C4%85%3A%C4%99?p%3A1=v%26v&p2=v+v"
  )

  for {
    (uri, expected) <- wholeUriTestData
  } {
    test(s"$uri should serialize to $expected") {
      uri.toString should be(expected)
    }
  }

  val testUri = Uri("http", "example.com", None, Nil, Nil, None)

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
}
