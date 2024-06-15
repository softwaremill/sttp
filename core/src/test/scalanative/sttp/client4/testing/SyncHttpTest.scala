package sttp.client4.testing

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

import sttp.client4._
import org.scalatest._
import org.scalatest.freespec.AnyFreeSpec
import sttp.model.StatusCode
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

// This is a synchronous version of sttp.client4.testing.HttpTest.
// It had to be copied, because there are no async test specs in scala-test.
// As soon as AsyncFreeSpec is released for Scala Native, this one should be drooped in favour of HttpTest.
// The progress can be tracked within this issue: https://github.com/scalatest/scalatest/issues/1112.
trait SyncHttpTest
    extends AnyFreeSpec
    with Matchers
    with ToFutureWrapper
    with OptionValues
    with EitherValues
    with BeforeAndAfterAll
    with SyncHttpTestExtensions {
  protected def endpoint: String = "http://localhost:51823"

  protected val binaryFileMD5Hash = "565370873a38d91f34a3091082e63933"
  protected val textFileMD5Hash = "b048a88ece8e4ec5eb386b8fc5006d13"

  val backend: SyncBackend

  protected def postEcho = basicRequest.post(uri"$endpoint/echo")
  protected def putEcho = basicRequest.put(uri"$endpoint/echo")
  protected val testBody = "this is the body"
  protected val testBodyBytes = testBody.getBytes("UTF-8")
  protected val expectedPostEchoResponse = "POST /echo this is the body"
  protected val expectedPutEchoResponse = "PUT /echo this is the body"

  protected val sttpIgnore = sttp.client4.ignore

  "parse response" - {
    "as string" in {
      val response = postEcho.body(testBody).send(backend)
      response.body should be(Right(expectedPostEchoResponse))
    }

    "as string with utf-8 characters" in {
      val response = postEcho.body("this is the body😀").send(backend)
      response.body should be(Right("POST /echo this is the body😀"))
    }

    "as string with mapping using map" in {
      val response = postEcho
        .body(testBody)
        .response(asString.mapRight(_.length))
        .send(backend)
      response.body should be(Right(expectedPostEchoResponse.length))
    }

    "as string with mapping using mapResponse" in {
      val response = postEcho
        .body(testBody)
        .mapResponseRight(_.length)
        .send(backend)
      response.body should be(Right(expectedPostEchoResponse.length))
    }

    "as a byte array" in {
      val response = postEcho
        .body(testBody)
        .response(asByteArrayAlways)
        .send(backend)
      val fc = new String(response.body, "UTF-8")
      fc should be(expectedPostEchoResponse)
    }

    "as parameters" in {
      val params = List("a" -> "b", "c" -> "d", "e=" -> "&f")
      val response = basicRequest
        .post(uri"$endpoint/echo/form_params/as_params")
        .body(params: _*)
        .response(asParams)
        .send(backend)
      response.body.right.map(_.toList) should be(Right(params))
    }

    "as string with response via metadata" in {
      val expectedStatus = StatusCode.BadRequest
      val response = basicRequest
        .post(uri"$endpoint/echo/custom_status/${expectedStatus.code}")
        .body(testBody)
        .response(asStringAlways.mapWithMetadata((r, m) => if (m.code == expectedStatus) Right(r) else Left(r)))
        .send(backend)

      response.body should be(Right(s"POST /echo/custom_status/${expectedStatus.code} $testBody"))
    }

    "as error string with response via metadata" in {
      val unexpectedStatus = StatusCode.Ok
      val response = basicRequest
        .post(uri"$endpoint/echo/custom_status/${unexpectedStatus.code}")
        .body(testBody)
        .response(asStringAlways.mapWithMetadata((r, m) => if (m.code == unexpectedStatus) Left(r) else Right(r)))
        .send(backend)

      response.body should be(Left(s"POST /echo/custom_status/${unexpectedStatus.code} $testBody"))
    }

    "as string, when the content type encoding is in quotes" in {
      val response = basicRequest
        .post(uri"$endpoint/set_content_type_header_with_encoding_in_quotes")
        .body(testBody)
        .send(backend)

      response.body should be(Right(testBody))
    }

    "as both string and mapped string" in {
      val response = postEcho
        .body(testBody)
        .response(asBoth(asStringAlways, asByteArray.mapRight(_.length)))
        .send(backend)

      response.body shouldBe ((expectedPostEchoResponse, Right(expectedPostEchoResponse.getBytes.length)))
    }
  }

  "parameters" - {
    "make a get request with parameters" in {
      val response = basicRequest
        .get(uri"$endpoint/echo?p2=v2&p1=v1")
        .send(backend)
      response.body should be(Right("GET /echo p1=v1 p2=v2"))
    }
  }

  "body" - {
    "post a string" in {
      val response = postEcho
        .body(testBody)
        .send(backend)
      response.body should be(Right(expectedPostEchoResponse))
    }

    "post a byte array" in {
      val response = postEcho.body(testBodyBytes).send(backend)
      response.body should be(Right(expectedPostEchoResponse))
    }

    "post an input stream" in {
      val response = postEcho
        .body(new ByteArrayInputStream(testBodyBytes))
        .send(backend)
      response.body should be(Right(expectedPostEchoResponse))
    }

    "post a byte buffer" in {
      val response = postEcho
        .body(ByteBuffer.wrap(testBodyBytes))
        .send(backend)
      response.body should be(Right(expectedPostEchoResponse))
    }

    "post form data" in {
      val response = basicRequest
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a" -> "b", "c" -> "d")
        .send(backend)
      response.body should be(Right("a=b c=d"))
    }

    "post form data with special characters" in {
      val response = basicRequest
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a=" -> "/b", "c:" -> "/d")
        .send(backend)
      response.body should be(Right("a==/b c:=/d"))
    }

    "post without a body" in {
      val response = postEcho.send(backend)
      response.body should be(Right("POST /echo"))
    }

    "put a string" in {
      val response = putEcho
        .body(testBody)
        .send(backend)
      response.body should be(Right(expectedPutEchoResponse))
    }

    "put a byte array" in {
      val response = putEcho.body(testBodyBytes).send(backend)
      response.body should be(Right(expectedPutEchoResponse))
    }

    "put an input stream" in {
      val response = putEcho
        .body(new ByteArrayInputStream(testBodyBytes))
        .send(backend)
      response.body should be(Right(expectedPutEchoResponse))
    }

    "put a byte buffer" in {
      val response = putEcho
        .body(ByteBuffer.wrap(testBodyBytes))
        .send(backend)
      response.body should be(Right(expectedPutEchoResponse))
    }

    "put form data" in {
      val response = basicRequest
        .put(uri"$endpoint/echo/form_params/as_string")
        .body("a" -> "b", "c" -> "d")
        .send(backend)
      response.body should be(Right("a=b c=d"))
    }

    "put form data with special characters" in {
      val response = basicRequest
        .put(uri"$endpoint/echo/form_params/as_string")
        .body("a=" -> "/b", "c:" -> "/d")
        .send(backend)
      response.body should be(Right("a==/b c:=/d"))
    }

    "put without a body" in {
      val response = putEcho.send(backend)
      response.body should be(Right("PUT /echo"))
    }
  }

  protected def cacheControlHeaders = Set("no-cache", "max-age=1000")

  "headers" - {
    def getHeaders = basicRequest.get(uri"$endpoint/set_headers")
    "read response headers" in {
      val response = getHeaders.response(sttpIgnore).send(backend)
      response.headers should have length (4 + cacheControlHeaders.size).toLong
      response.headers("Cache-Control").toSet should be(cacheControlHeaders)
      response.header("Server").exists(_.startsWith("akka-http")) should be(true)
      response.contentType should be(Some("text/plain; charset=UTF-8"))
      response.contentLength should be(Some(2L))
    }
  }

  "errors" - {
    "return 405 when method not allowed" in {
      val response = basicRequest.post(uri"$endpoint/set_headers").response(sttpIgnore).send(backend)
      response.code shouldBe StatusCode.MethodNotAllowed
      response.isClientError should be(true)
    }

    "return 404 when not found" in {
      val response = basicRequest.get(uri"$endpoint/not/found").response(sttpIgnore).send(backend)
      response.code shouldBe StatusCode.NotFound
      response.isClientError should be(true)
    }
  }

  "auth" - {
    def secureBasic = basicRequest.get(uri"$endpoint/secure_basic")

    "return a 401 when authorization fails" in {
      val req = secureBasic
      val resp = req.send(backend)
      resp.code shouldBe StatusCode.Unauthorized
      resp.header("WWW-Authenticate") shouldBe Some("""Basic realm="test realm",charset=UTF-8""")
    }

    "perform basic authorization" in {
      val req = secureBasic.auth.basic("adam", "1234")
      val resp = req.send(backend)
      resp.code shouldBe StatusCode.Ok
      resp.body shouldBe Right("Hello, adam!")
    }
  }

  "compression" - {
    def compress = basicRequest.get(uri"$endpoint/compress")
    val decompressedBody = "I'm compressed!"

    "decompress using the default accept encoding header" in {
      val req = compress
      val resp = req.send(backend)
      resp.body should be(Right(decompressedBody))
    }

    "decompress using gzip" in {
      val req = compress.header("Accept-Encoding", "gzip")
      val resp = req.send(backend)
      resp.body should be(Right(decompressedBody))
    }

    "decompress using deflate" in {
      val req = compress.header("Accept-Encoding", "deflate")
      val resp = req.send(backend)
      resp.body should be(Right(decompressedBody))
    }

    "work despite providing an unsupported encoding" in {
      val req = compress.header("Accept-Encoding", "br")
      val resp = req.send(backend)
      resp.body should be(Right(decompressedBody))
    }
  }

  // in JavaScript the only way to set the content type is to use a Blob which defaults the filename to 'blob'
  protected def multipartStringDefaultFileName: Option[String] = None
  protected def defaultFileName =
    multipartStringDefaultFileName match {
      case None       => ""
      case Some(name) => s" ($name)"
    }

  "multipart" - {
    def mp = basicRequest.post(uri"$endpoint/multipart")

    "send a multipart message" in {
      val req = mp.multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
      val resp = req.send(backend)
      resp.body should be(Right(s"p1=v1$defaultFileName, p2=v2$defaultFileName"))
    }

    "send a multipart message with filenames" in {
      val req = mp.multipartBody(multipart("p1", "v1").fileName("f1"), multipart("p2", "v2").fileName("f2"))
      val resp = req.send(backend)
      resp.body should be(Right("p1=v1 (f1), p2=v2 (f2)"))
    }
  }

  "redirect" - {
    def r1 = basicRequest.post(uri"$endpoint/redirect/r1").response(asStringAlways)
    def r2 = basicRequest.post(uri"$endpoint/redirect/r2").response(asStringAlways)
    val r4response = "819"
    def loop = basicRequest.post(uri"$endpoint/redirect/loop").response(asStringAlways)

    "not redirect when redirects shouldn't be followed (temporary)" in {
      val resp = r1.followRedirects(false).send(backend)
      resp.code.code should be(307)
      resp.body should be(
        "The request should be repeated with <a href=\"/redirect/r2\">this URI</a>, but future requests can still use the original URI."
      )
      resp.history should be(Nil)
    }

    "not redirect when redirects shouldn't be followed (permanent)" in {
      val resp = r2.followRedirects(false).send(backend)
      resp.code.code should be(308)
      resp.body should be(
        "The request, and all future requests should be repeated using <a href=\"/redirect/r3\">this URI</a>."
      )
      resp.history should be(Nil)
    }

    "redirect when redirects should be followed" in {
      val resp = r2.send(backend)
      resp.code shouldBe StatusCode.Ok
      resp.body should be(r4response)
    }

    "redirect twice when redirects should be followed" in {
      val resp = r1.send(backend)
      resp.code shouldBe StatusCode.Ok
      resp.body should be(r4response)
    }

    "redirect when redirects should be followed, and the response is parsed" in {
      val resp = r2.response(asString.mapRight(_.toInt)).send(backend)
      resp.code shouldBe StatusCode.Ok
      resp.body should be(Right(r4response.toInt))
    }

    "not redirect when maxRedirects is less than or equal to 0" in {
      val resp = loop.maxRedirects(-1).send(backend)
      resp.code shouldBe StatusCode.Found
      resp.body should be(
        "The requested resource temporarily resides under <a href=\"/redirect/loop\">this URI</a>."
      )
      resp.history should be(Nil)
    }
  }

  "timeout" - {
    "fail if read timeout is not big enough" in {
      val req = basicRequest
        .get(uri"$endpoint/timeout")
        .readTimeout(200.milliseconds)
        .response(asString)
      val caught = intercept[RuntimeException](req.send(backend))
      caught.getMessage should include("TIMEDOUT")
    }

    "not fail if read timeout is big enough" in {
      val req = basicRequest
        .get(uri"$endpoint/timeout")
        .readTimeout(5.seconds)
        .response(asString)

      val response = req.send(backend)
      response.body should be(Right("Done"))
    }
  }

  "empty response" - {
    def postEmptyResponse =
      basicRequest
        .post(uri"$endpoint/empty_unauthorized_response")
        .body("{}")
        .contentType("application/json")

    "parse an empty error response as empty string" in {
      postEmptyResponse.send(backend).body.left.value should be("")
    }
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
