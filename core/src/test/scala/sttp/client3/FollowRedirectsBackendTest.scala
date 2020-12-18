package sttp.client3

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FollowRedirectsBackendTest extends AnyFunSuite with Matchers {
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

  for ((uri, isRelative) <- testData) {
    test(s"$uri should ${if (isRelative) "" else "not "}be relative") {
      FollowRedirectsBackend.isRelative(uri) shouldBe isRelative
    }
  }
}
