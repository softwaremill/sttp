package com.softwaremill.sttp.testing

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

import com.softwaremill.sttp.file.File
import com.softwaremill.sttp._
import org.scalatest.{AsyncFreeSpec, BeforeAndAfterAll, Matchers, OptionValues}

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.higherKinds

trait HttpTest[R[_]]
    extends AsyncFreeSpec
    with Matchers
    with ToFutureWrapper
    with OptionValues
    with BeforeAndAfterAll
    with HttpTestExtensions[R] {

  protected def endpoint: String

  protected val binaryFileHash = "2fab8aa91221d3ce09599386643995fc90554f729739c7082b499ae7a720de30"
  protected val textFileHash = "c57c912fdd2f88f1e8d09d4514744cf706809dd7e352f9aad3ef9bcb586a46a4"
  private val textWithSpecialCharacters = "Żółć!"

  implicit val backend: SttpBackend[R, Nothing]
  implicit val convertToFuture: ConvertToFuture[R]

  private def postEcho = sttp.post(uri"$endpoint/echo")
  protected val testBody = "this is the body"
  protected val testBodyBytes = testBody.getBytes("UTF-8")
  private val expectedPostEchoResponse = "POST /echo this is the body"

  protected val sttpIgnore = com.softwaremill.sttp.ignore

  "parse response" - {
    "as string" in {
      postEcho.body(testBody).send().toFuture().map { response =>
        response.unsafeBody should be(expectedPostEchoResponse)
      }
    }

    "as string with mapping using map" in {
      postEcho
        .body(testBody)
        .response(asString.map(_.length))
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be(expectedPostEchoResponse.length)
        }
    }

    "as string with mapping using mapResponse" in {
      postEcho
        .body(testBody)
        .mapResponse(_.length)
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be(expectedPostEchoResponse.length)
        }
    }

    "as a byte array" in {
      postEcho.body(testBody).response(asByteArray).send().toFuture().map { response =>
        val fc = new String(response.unsafeBody, "UTF-8")
        fc should be(expectedPostEchoResponse)
      }
    }

    "as parameters" in {
      val params = List("a" -> "b", "c" -> "d", "e=" -> "&f")
      sttp
        .post(uri"$endpoint/echo/form_params/as_params")
        .body(params: _*)
        .response(asParams)
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody.toList should be(params)
        }
    }
  }

  "parameters" - {
    "make a get request with parameters" in {
      sttp
        .get(uri"$endpoint/echo?p2=v2&p1=v1")
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be("GET /echo p1=v1 p2=v2")
        }
    }
  }

  "body" - {
    "post a string" in {
      postEcho.body(testBody).send().toFuture().map { response =>
        response.unsafeBody should be(expectedPostEchoResponse)
      }
    }

    "post a byte array" in {
      postEcho.body(testBodyBytes).send().toFuture().map { response =>
        response.unsafeBody should be(expectedPostEchoResponse)
      }
    }

    "post an input stream" in {
      postEcho
        .body(new ByteArrayInputStream(testBodyBytes))
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be(expectedPostEchoResponse)
        }
    }

    "post a byte buffer" in {
      postEcho
        .body(ByteBuffer.wrap(testBodyBytes))
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be(expectedPostEchoResponse)
        }
    }

    "post a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        postEcho.body(f).send().toFuture().map { response =>
          response.unsafeBody should be(expectedPostEchoResponse)
        }
      }
    }

    "post form data" in {
      sttp
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a" -> "b", "c" -> "d")
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be("a=b c=d")
        }
    }

    "post form data with special characters" in {
      sttp
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a=" -> "/b", "c:" -> "/d")
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be("a==/b c:=/d")
        }
    }

    "post without a body" in {
      postEcho.send().toFuture().map { response =>
        response.unsafeBody should be("POST /echo")
      }
    }
  }

  "headers" - {
    def getHeaders = sttp.get(uri"$endpoint/set_headers")

    "read response headers" in {
      getHeaders.response(sttpIgnore).send().toFuture().map { response =>
        response.headers should have length (6)
        response.headers("Cache-Control").toSet should be(Set("no-cache", "max-age=1000"))
        response.header("Server").exists(_.startsWith("akka-http")) should be(true)
        response.contentType should be(Some("text/plain; charset=UTF-8"))
        response.contentLength should be(Some(2L))
      }
    }
  }

  "errors" - {
    def getHeaders = sttp.post(uri"$endpoint/set_headers")

    "return 405 when method not allowed" in {
      getHeaders.response(sttpIgnore).send().toFuture().map { response =>
        response.code should be(405)
        response.isClientError should be(true)
        response.body.isLeft should be(true)
      }
    }
  }

  "auth" - {
    def secureBasic = sttp.get(uri"$endpoint/secure_basic")

    "return a 401 when authorization fails" in {
      val req = secureBasic
      req.send().toFuture().map { resp =>
        resp.code should be(401)
        resp.header("WWW-Authenticate") should be(Some("""Basic realm="test realm",charset=UTF-8"""))
      }
    }

    "perform basic authorization" in {
      val req = secureBasic.auth.basic("adam", "1234")
      req.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be("Hello, adam!")
      }
    }
  }

  "compression" - {
    def compress = sttp.get(uri"$endpoint/compress")
    val decompressedBody = "I'm compressed!"

    "decompress using the default accept encoding header" in {
      val req = compress
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(decompressedBody)
      }
    }

    "decompress using gzip" in {
      val req = compress.header("Accept-Encoding", "gzip", replaceExisting = true)
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(decompressedBody)
      }
    }

    "decompress using deflate" in {
      val req = compress.header("Accept-Encoding", "deflate", replaceExisting = true)
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(decompressedBody)
      }
    }

    "work despite providing an unsupported encoding" in {
      val req = compress.header("Accept-Encoding", "br", replaceExisting = true)
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(decompressedBody)
      }
    }
  }

  protected def withTemporaryFile[T](content: Option[Array[Byte]])(f: File => Future[T]): Future[T]
  private def withTemporaryNonExistentFile[T](f: File => Future[T]): Future[T] = withTemporaryFile(None)(f)

  protected def sha256Hash(bytes: Array[Byte]): String
  protected def sha256FileHash(file: File): String

  "download file" - {

    "download a binary file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = sttp.get(uri"$endpoint/download/binary").response(asFile(file))
        req.send().toFuture().map { resp =>
          sha256FileHash(resp.unsafeBody) shouldBe binaryFileHash
        }
      }
    }

    "download a text file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = sttp.get(uri"$endpoint/download/text").response(asFile(file))
        req.send().toFuture().map { resp =>
          sha256FileHash(resp.unsafeBody) shouldBe textFileHash
        }
      }
    }
  }

  "multipart" - {
    def mp = sttp.post(uri"$endpoint/multipart")

    "send a multipart message" in {
      val req = mp.multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be("p1=v1, p2=v2")
      }
    }

    "send a multipart message with filenames" in {
      val req = mp.multipartBody(multipart("p1", "v1").fileName("f1"), multipart("p2", "v2").fileName("f2"))
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be("p1=v1 (f1), p2=v2 (f2)")
      }
    }

    "send a multipart message with a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        val req = mp.multipartBody(multipart("p1", f), multipart("p2", "v2"))
        req.send().toFuture().map { resp =>
          resp.unsafeBody should be(s"p1=$testBody (${f.name}), p2=v2")
        }
      }
    }
  }

  "redirect" - {
    def r1 = sttp.post(uri"$endpoint/redirect/r1")
    def r2 = sttp.post(uri"$endpoint/redirect/r2")
    def r3 = sttp.post(uri"$endpoint/redirect/r3")
    val r4response = "819"
    def loop = sttp.post(uri"$endpoint/redirect/loop")

    "not redirect when redirects shouldn't be followed (temporary)" in {
      r1.followRedirects(false).send().toFuture().map { resp =>
        resp.code should be(307)
        resp.body should be('left)
        resp.history should be('empty)
      }
    }

    "not redirect when redirects shouldn't be followed (permanent)" in {
      r2.followRedirects(false).send().toFuture().map { resp =>
        resp.code should be(308)
        resp.body should be('left)
      }
    }

    "redirect when redirects should be followed" in {
      r2.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
      }
    }

    "redirect twice when redirects should be followed" in {
      r1.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
      }
    }

    "redirect when redirects should be followed, and the response is parsed" in {
      r2.response(asString.map(_.toInt)).send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response.toInt)
      }
    }

    "keep a single history entry of redirect responses" in {
      r3.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
        resp.history should have size (1)
        resp.history(0).code should be(302)
      }
    }

    "keep whole history of redirect responses" in {
      r1.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
        resp.history should have size (3)
        resp.history(0).code should be(307)
        resp.history(1).code should be(308)
        resp.history(2).code should be(302)
      }
    }

    "break redirect loops" in {
      loop.send().toFuture().map { resp =>
        resp.code should be(0)
        resp.history should have size (FollowRedirectsBackend.MaxRedirects)
      }
    }

    "break redirect loops after user-specified count" in {
      val maxRedirects = 10
      loop.maxRedirects(maxRedirects).send().toFuture().map { resp =>
        resp.code should be(0)
        resp.history should have size (maxRedirects)
      }
    }

    "not redirect when maxRedirects is less than or equal to 0" in {
      loop.maxRedirects(-1).send().toFuture().map { resp =>
        resp.code should be(302)
        resp.body should be('left)
        resp.history should be('empty)
      }
    }
  }

  "timeout" - {
    "fail if read timeout is not big enough" in {
      val request = sttp
        .get(uri"$endpoint/timeout")
        .readTimeout(200.milliseconds)
        .response(asString)

      Future(request.send()).flatMap(_.toFuture()).failed.map { _ =>
        succeed
      }
    }

    "not fail if read timeout is big enough" in {
      val request = sttp
        .get(uri"$endpoint/timeout")
        .readTimeout(5.seconds)
        .response(asString)

      request.send().toFuture().map { response =>
        response.unsafeBody should be("Done")
      }
    }
  }

  "empty response" - {
    def postEmptyResponse =
      sttp
        .post(uri"$endpoint/empty_unauthorized_response")
        .body("{}")
        .contentType("application/json")

    "parse an empty error response as empty string" in {
      postEmptyResponse.send().toFuture().map { response =>
        response.body should be(Left(""))
      }
    }
  }

  "encoding" - {
    "read response body encoded using ISO-8859-2, as specified in the header, overriding the default" in {
      val request = sttp.get(uri"$endpoint/respond_with_iso_8859_2")

      request.send().toFuture().map { response =>
        response.unsafeBody should be(textWithSpecialCharacters)
      }
    }
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }

}
