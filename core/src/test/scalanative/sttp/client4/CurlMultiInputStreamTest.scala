package sttp.client4

import sttp.client4.curl.CurlBackend
import sttp.client4.testing.SyncHttpTest

import java.io.IOException
import scala.concurrent.duration._

class CurlMultiInputStreamTest extends SyncHttpTest {
  override implicit val backend: SyncBackend = CurlBackend(verbose = false)

  "streaming InputStream" - {
    "read response body as InputStream with function" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello streaming world")
        .response(asInputStreamAlways { is =>
          val sb = new StringBuilder
          var b = is.read()
          while (b != -1) {
            sb.append(b.toChar)
            b = is.read()
          }
          sb.toString
        })
        .send(backend)
      response.body should be("POST /echo hello streaming world")
    }

    "read response body as InputStream with bulk read" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello streaming world")
        .response(asInputStreamAlways { is =>
          val buf = new Array[Byte](8192)
          val baos = new java.io.ByteArrayOutputStream()
          var n = is.read(buf)
          while (n != -1) {
            baos.write(buf, 0, n)
            n = is.read(buf)
          }
          new String(baos.toByteArray, "UTF-8")
        })
        .send(backend)
      response.body should be("POST /echo hello streaming world")
    }

    "read response body as unsafe InputStream" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello streaming world")
        .response(asInputStreamAlwaysUnsafe)
        .send(backend)
      try {
        val buf = new Array[Byte](8192)
        val baos = new java.io.ByteArrayOutputStream()
        var n = response.body.read(buf)
        while (n != -1) {
          baos.write(buf, 0, n)
          n = response.body.read(buf)
        }
        new String(baos.toByteArray, "UTF-8") should be("POST /echo hello streaming world")
      } finally response.body.close()
    }

    "handle asInputStreamUnsafe with asEither (success path)" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello")
        .response(asInputStreamUnsafe)
        .send(backend)
      response.body.isRight should be(true)
      val is = response.body.right.get
      try {
        val buf = new Array[Byte](8192)
        val n = is.read(buf)
        new String(buf, 0, n, "UTF-8") should be("POST /echo hello")
      } finally is.close()
    }

    "handle asInputStreamUnsafe with asEither (error path)" in {
      val response = basicRequest
        .get(uri"$endpoint/not/found")
        .response(asInputStreamUnsafe)
        .send(backend)
      response.body.isLeft should be(true)
      response.body.left.get should not be empty
    }

    "handle empty response body" in {
      val response = basicRequest
        .post(uri"$endpoint/empty_unauthorized_response")
        .body("{}")
        .contentType("application/json")
        .response(asInputStreamAlways { is =>
          val buf = new Array[Byte](8192)
          val n = is.read(buf)
          if (n == -1) "" else new String(buf, 0, n, "UTF-8")
        })
        .send(backend)
      response.body should be("")
    }

    "handle large response body" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("x" * 100000)
        .response(asInputStreamAlways { is =>
          val buf = new Array[Byte](8192)
          val baos = new java.io.ByteArrayOutputStream()
          var n = is.read(buf)
          while (n != -1) {
            baos.write(buf, 0, n)
            n = is.read(buf)
          }
          baos.size
        })
        .send(backend)
      response.body should be > 100000 // "POST /echo " + 100K x's
    }

    "close InputStream before fully reading" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello")
        .response(asInputStreamAlwaysUnsafe)
        .send(backend)
      val is = response.body
      val firstByte = is.read()
      firstByte should be > 0
      is.close() // close without reading everything — should not crash/leak
    }

    "available() returns correct value" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello")
        .response(asInputStreamAlwaysUnsafe)
        .send(backend)
      val is = response.body
      try {
        val buf = new Array[Byte](1)
        is.read(buf) // triggers first pump
        is.available() should be >= 0
      } finally is.close()
    }

    // --- Bug reproduction tests ---

    "connection refused should throw a meaningful error, not NoSuchElementException" in {
      val thrown = the[Exception] thrownBy {
        basicRequest
          .get(uri"http://localhost:1/nope")
          .response(asInputStreamAlways { is =>
            is.read()
          })
          .send(backend)
      }
      thrown shouldNot be(a[NoSuchElementException])
    }

    "asBothOption with InputStream should return a valid tuple" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello")
        .response(
          asBothOption(
            asInputStreamAlways { is =>
              val buf = new Array[Byte](8192)
              val n = is.read(buf)
              new String(buf, 0, n, "UTF-8")
            },
            asStringAlways
          )
        )
        .send(backend)
      response.body._1 should not be empty
      response.body._2 shouldBe None // InputStream is not replayable, so right side is None
    }

    // --- Robustness tests ---

    "double close should be idempotent" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello")
        .response(asInputStreamAlwaysUnsafe)
        .send(backend)
      val is = response.body
      is.read() // trigger first pump
      is.close()
      is.close() // second close should not crash or double-free
    }

    "read after close should throw IOException" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello")
        .response(asInputStreamAlwaysUnsafe)
        .send(backend)
      val is = response.body
      is.close()
      the[IOException] thrownBy is.read()
    }

    "multiple sequential requests should not leak resources" in {
      for (i <- 1 to 100) {
        val response = basicRequest
          .post(uri"$endpoint/echo")
          .body(s"request $i")
          .response(asInputStreamAlways { is =>
            val buf = new Array[Byte](8192)
            val baos = new java.io.ByteArrayOutputStream()
            var n = is.read(buf)
            while (n != -1) {
              baos.write(buf, 0, n)
              n = is.read(buf)
            }
            baos.size
          })
          .send(backend)
        response.body should be > 0
      }
    }

    "server closes connection mid-stream" in {
      val thrown = the[Exception] thrownBy {
        basicRequest
          .get(uri"$endpoint/error")
          .response(asInputStreamAlways { is =>
            val buf = new Array[Byte](8192)
            val baos = new java.io.ByteArrayOutputStream()
            var n = is.read(buf)
            while (n != -1) {
              baos.write(buf, 0, n)
              n = is.read(buf)
            }
            baos.toByteArray
          })
          .send(backend)
      }
      // Should get some kind of error, not hang forever
      thrown should not be null
    }

    "read timeout during streaming" in {
      val thrown = the[Exception] thrownBy {
        basicRequest
          .get(uri"$endpoint/timeout")
          .readTimeout(200.milliseconds)
          .response(asInputStreamAlways { is =>
            val buf = new Array[Byte](8192)
            var n = is.read(buf)
            while (n != -1) {
              n = is.read(buf)
            }
          })
          .send(backend)
      }
      thrown should not be null
    }

    "trickle server delivers data correctly" in {
      val response = basicRequest
        .get(uri"$endpoint/streaming/slow")
        .readTimeout(10.seconds)
        .response(asInputStreamAlways { is =>
          val buf = new Array[Byte](8192)
          val baos = new java.io.ByteArrayOutputStream()
          var n = is.read(buf)
          while (n != -1) {
            baos.write(buf, 0, n)
            n = is.read(buf)
          }
          baos.size
        })
        .send(backend)
      response.body should be(20) // 20 chunks of "a"
    }

    "asInputStream with mapping" in {
      val response = basicRequest
        .post(uri"$endpoint/echo")
        .body("hello")
        .response(asInputStreamAlways { is =>
          val buf = new Array[Byte](8192)
          val n = is.read(buf)
          new String(buf, 0, n, "UTF-8")
        }.map(_.length))
        .send(backend)
      response.body should be > 0
    }
  }
}
