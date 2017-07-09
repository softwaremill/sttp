package com.softwaremill.sttp

import java.net.URI

import org.scalatest.{FlatSpec, Matchers}

class UriInterpolatorTests extends FlatSpec with Matchers {
  val v1 = "y"
  val v2 = "a c"
  val v2encoded = "a%20c"

  val testData: List[(URI, String)] = List(
    (uri"http://example.com", "http://example.com"),
    (uri"http://example.com?x=y", "http://example.com?x=y"),
    (uri"http://example.com?x=$v1", s"http://example.com?x=$v1"),
    (uri"http://example.com?x=$v2", s"http://example.com?x=$v2encoded"),
    (uri"http://$v1.com", s"http://$v1.com"),
    (uri"http://$v1.com?x=$v2", s"http://$v1.com?x=$v2encoded")
  )

  for (((interpolated, expected), i) <- testData.zipWithIndex) {
    it should s"interpolate to $expected ($i)" in {
      interpolated should be (new URI(expected))
    }
  }
}
