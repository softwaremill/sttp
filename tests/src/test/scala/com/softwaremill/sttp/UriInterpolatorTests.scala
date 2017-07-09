package com.softwaremill.sttp

import java.net.URI

import org.scalatest.{FunSuite, Matchers}

class UriInterpolatorTests extends FunSuite with Matchers {
  val v1 = "y"
  val v2 = "a c"
  val v2queryEncoded = "a+c"
  val v2hostEncoded = "a%20c"
  val secure = true

  val testData: List[(String, List[(URI, String)])] = List(
    "basic" -> List(
      (uri"http://example.com", "http://example.com"),
      (uri"http://example.com?x=y", "http://example.com?x=y")
    ),
    "scheme" -> List(
      (uri"http${if (secure) "s" else ""}://example.com",
       s"https://example.com"),
      (uri"${if (secure) "https" else "http"}://example.com",
       s"https://example.com"),
      (uri"${if (secure) "https://" else "http://"}example.com",
       s"https://example.com"),
      (uri"example.com?a=$v2", s"example.com?a=$v2queryEncoded")
    ),
    "authority" -> List(
      (uri"http://$v1.com", s"http://$v1.com"),
      (uri"http://$v2.com", s"http://$v2hostEncoded.com"),
      (uri"http://$None.example.com", s"http://example.com"),
      (uri"http://$None.$None.example.com", s"http://example.com"),
      (uri"http://${Some("sub")}.example.com", s"http://sub.example.com"),
      (uri"http://${Some("sub1.sub2")}.example.com",
       s"http://sub1.sub2.example.com"),
      (uri"http://${List("sub1", "sub2")}.example.com",
       s"http://sub1.sub2.example.com"),
      (uri"http://${List("sub", "example", "com")}", s"http://sub.example.com")
    ),
    "authority with parameters" -> List(
      (uri"http://$v1.com?x=$v2", s"http://$v1.com?x=$v2queryEncoded")
    ),
    "query parameter values" -> List(
      (uri"http://example.com?x=$v1", s"http://example.com?x=$v1"),
      (uri"http://example.com?x=$v2", s"http://example.com?x=$v2queryEncoded")
    ),
    "optional query parameters" -> List(
      (uri"http://example.com?a=$None", s"http://example.com"),
      (uri"http://example.com?a=b&c=$None", s"http://example.com?a=b"),
      (uri"http://example.com?a=b&c=$None&e=f", s"http://example.com?a=b&e=f"),
      (uri"http://example.com?a=${Some(v1)}", s"http://example.com?a=$v1"),
      (uri"http://example.com?a=${Some(v1)}&c=d",
       s"http://example.com?a=$v1&c=d")
    ),
    "embed whole url" -> List(
      (uri"${"http://example.com/a/b?x=y&1=2"}",
       s"http://example.com/a/b?x=y&1=2")
    )
  )

  for {
    (groupName, testCases) <- testData
    ((interpolated, expected), i) <- testCases.zipWithIndex
  } {
    test(s"[$groupName] interpolate to $expected (${i + 1})") {
      interpolated should be(new URI(expected))
    }
  }
}
