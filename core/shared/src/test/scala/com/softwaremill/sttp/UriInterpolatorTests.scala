package com.softwaremill.sttp

import org.scalatest.{FunSuite, Matchers}

class UriInterpolatorTests extends FunSuite with Matchers {
  val v1 = "y"
  val v2 = "a c"
  val v2queryEncoded = "a+c"
  val v2encoded = "a%20c"
  val v3 = "a?=&c"
  val v3encoded = "a?%3D%26c"
  val v4 = "f/g"
  val v4encoded = "f%2Fg"
  val v5 = "a:b"
  val v5encoded = "a%3Ab"
  val secure = true

  val testData: List[(String, List[(Uri, String)])] = List(
    "basic" -> List(
      (uri"http://example.com", "http://example.com"),
      (uri"http://example.com/", "http://example.com/"),
      (uri"http://example.com?x=y", "http://example.com?x=y"),
      (uri"http://example.com/a/b/c", "http://example.com/a/b/c"),
      (uri"http://example.com/a/b/c/", "http://example.com/a/b/c/"),
      (uri"http://example.com/a/b/c?x=y&h=j", "http://example.com/a/b/c?x=y&h=j")
    ),
    "scheme" -> List(
      (uri"http${if (secure) "s" else ""}://example.com", s"https://example.com"),
      (uri"${if (secure) "https" else "http"}://example.com", s"https://example.com"),
      (uri"example.com?a=$v2", s"http://example.com?a=$v2queryEncoded")
    ),
    "user info" -> List(
      (uri"http://user:pass@example.com", s"http://user:pass@example.com"),
      (uri"http://$v2@example.com", s"http://$v2encoded@example.com"),
      (uri"http://$v5@example.com", s"http://$v5encoded@example.com"),
      (uri"http://$v1:$v2@example.com", s"http://$v1:$v2encoded@example.com")
    ),
    "authority" -> List(
      (uri"http://$v1.com", s"http://$v1.com"),
      (uri"http://z$v1.com", s"http://z$v1.com"),
      (uri"http://$None.example.com", s"http://example.com"),
      (uri"http://$None.$None.example.com", s"http://example.com"),
      (uri"http://${Some("sub")}.example.com", s"http://sub.example.com"),
      (uri"http://${Some("sub1.sub2")}.example.com", s"http://sub1.sub2.example.com"),
      (uri"http://${List("sub1", "sub2")}.example.com", s"http://sub1.sub2.example.com"),
      (uri"http://${List("sub", "example", "com")}", s"http://sub.example.com")
    ),
    "authority with parameters" -> List(
      (uri"http://$v1.com?x=$v2", s"http://$v1.com?x=$v2queryEncoded")
    ),
    "ipv4" -> List(
      (uri"http://192.168.1.2/x", s"http://192.168.1.2/x"),
      (uri"http://${"192.168.1.2"}/x", s"http://192.168.1.2/x")
    ),
    "ipv6" -> List(
      (uri"http://[::1]/x", s"http://[::1]/x"),
      (uri"http://${"::1"}/x", s"http://[::1]/x"),
      (uri"http://${"::1"}:${8080}/x", s"http://[::1]:8080/x")
    ),
    "ports" -> List(
      (uri"http://example.com:8080", s"http://example.com:8080"),
      (uri"http://example.com:${8080}", s"http://example.com:8080"),
      (uri"http://example.com:${8080}/x", s"http://example.com:8080/x"),
      (uri"http://example.com:${Some(8080)}/x", s"http://example.com:8080/x"),
      (uri"http://example.com:$None/x", s"http://example.com/x"),
      (uri"http://${"example.com:8080"}", s"http://example.com:8080")
    ),
    "path" -> List(
      (uri"http://example.com/$v1", s"http://example.com/$v1"),
      (uri"http://example.com/$v1/", s"http://example.com/$v1/"),
      (uri"http://example.com/$v2", s"http://example.com/$v2encoded"),
      (uri"http://example.com/$v2/$v1", s"http://example.com/$v2encoded/$v1"),
      (uri"http://example.com/$v1/p/$v4", s"http://example.com/$v1/p/$v4encoded"),
      (uri"http://example.com/a/${List(v2, "c", v4)}/b", s"http://example.com/a/$v2encoded/c/$v4encoded/b"),
      (uri"http://example.com/${"a/b/c".split('/')}", s"http://example.com/a/b/c")
    ),
    "path with parameters" -> List(
      (uri"http://example.com/$v1?x=$v2", s"http://example.com/$v1?x=$v2queryEncoded"),
      (uri"http://example.com/$v1/$v2?x=$v2", s"http://example.com/$v1/$v2encoded?x=$v2queryEncoded")
    ),
    "query parameter values" -> List(
      (uri"http://example.com?x=$v1", s"http://example.com?x=$v1"),
      (uri"http://example.com/?x=$v1", s"http://example.com/?x=$v1"),
      (uri"http://example.com?x=$v2", s"http://example.com?x=$v2queryEncoded"),
      (uri"http://example.com?x=$v3", s"http://example.com?x=$v3encoded"),
      (uri"http://example.com?x=$v1$v1", s"http://example.com?x=$v1$v1"),
      (uri"http://example.com?x=z$v1", s"http://example.com?x=z$v1")
    ),
    "query parameter without value" -> List(
      (uri"http://example.com?$v1", s"http://example.com?$v1"),
      (uri"http://example.com?$v1&$v2", s"http://example.com?$v1&$v2queryEncoded")
    ),
    "optional query parameters" -> List(
      (uri"http://example.com?a=$None", s"http://example.com"),
      (uri"http://example.com?a=b&c=$None", s"http://example.com?a=b"),
      (uri"http://example.com?a=b&c=$None&e=f", s"http://example.com?a=b&e=f"),
      (uri"http://example.com?a=${Some(v1)}", s"http://example.com?a=$v1"),
      (uri"http://example.com?a=${Some(v1)}&c=d", s"http://example.com?a=$v1&c=d")
    ),
    "parameter collections" -> List(
      (uri"http://example.com?${Seq("a" -> "b", v2 -> v1, v1 -> v2)}",
       s"http://example.com?a=b&$v2queryEncoded=$v1&$v1=$v2queryEncoded"),
      (uri"http://example.com?${Seq("a" -> "b", "a" -> "c")}", s"http://example.com?a=b&a=c"),
      (uri"http://example.com?${Map("a" -> "b")}", s"http://example.com?a=b"),
      (uri"http://example.com?x=y&${Map("a" -> "b")}", s"http://example.com?x=y&a=b"),
      (uri"http://example.com?x=y&${Map("a" -> None)}", s"http://example.com?x=y"),
      (uri"http://example.com?x=y&${Map("a" -> Some("b"))}", s"http://example.com?x=y&a=b"),
      (uri"http://example.com?x=y&${Seq("a" -> None)}", s"http://example.com?x=y")
    ),
    "fragments" -> List(
      (uri"http://example.com#$v1", s"http://example.com#$v1"),
      (uri"http://example.com#$None", s"http://example.com")
    ),
    "everything" -> List(
      (uri"${"http"}://$v1.$v1.com/$v1/$v2?$v1=$v2&$v3=$v4#$v1",
       s"http://$v1.$v1.com/$v1/$v2encoded?$v1=$v2queryEncoded&$v3encoded=$v4#$v1")
    ),
    "embed whole url" -> List(
      (uri"${"http://example.com:123/a"}/b/c", "http://example.com:123/a/b/c"),
      (uri"${uri"http://example.com/$v1?p=$v2"}", s"http://example.com/$v1?p=$v2queryEncoded")
    )
  )

  for {
    (groupName, testCases) <- testData
    ((interpolated, expected), i) <- testCases.zipWithIndex
  } {
    test(s"[$groupName] should interpolate to $expected (${i + 1})") {
      interpolated.toString should be(expected)
    }
  }

  val validationTestData = List(
    ("uri with two ports", () => uri"http://example.com:80:80", "port specified multiple times"),
    ("uri with embedded host+port and port", () => uri"http://${"example.com:80"}:80", "port specified multiple times")
  )

  for {
    (name, createUri, expectedException) <- validationTestData
  } {
    test(s"""$name should validate and throw "$expectedException" if not valid""") {
      val caught = intercept[IllegalArgumentException] {
        createUri()
      }

      caught.getMessage.toLowerCase() should include(expectedException)
    }
  }
}
