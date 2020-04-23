package sttp.client.testing

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

import org.scalatest._
import sttp.client.{Response, ResponseAs, SttpBackend, _}
import sttp.model.StatusCode

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds
import org.scalatest.freespec.{AsyncFreeSpec, AsyncFreeSpecLike}
import org.scalatest.matchers.should.Matchers
import HttpTest.endpoint

// TODO: change to `extends AsyncFreeSpec` when https://github.com/scalatest/scalatest/issues/1802 is fixed
trait HttpTest[F[_]]
    extends SuiteMixin
    with AsyncFreeSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ToFutureWrapper
    with OptionValues
    with HttpTestExtensions[F] {

  protected val binaryFileMD5Hash = "565370873a38d91f34a3091082e63933"
  protected val textFileMD5Hash = "b048a88ece8e4ec5eb386b8fc5006d13"

  implicit val backend: SttpBackend[F, Nothing, NothingT]
  implicit val convertToFuture: ConvertToFuture[F]

  protected def postEcho: Request[Either[String, String], Nothing] = basicRequest.post(uri"$endpoint/echo")
  protected val testBody = "this is the body"
  protected val testBodyBytes: Array[Byte] = testBody.getBytes("UTF-8")
  protected val expectedPostEchoResponse = "POST /echo this is the body"

  protected val sttpIgnore: ResponseAs[Unit, Nothing] = sttp.client.ignore

  protected def supportsRequestTimeout = true
  protected def supportsSttpExceptions = true

  "parse response" - {
    "as string" in {
      postEcho.body(testBody).send().toFuture().map { response =>
        response.body should be(Right(expectedPostEchoResponse))
      }
    }

    "as string with mapping using map" in {
      postEcho
        .body(testBody)
        .response(asString.mapRight(_.length))
        .send()
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse.length)) }
    }

    "as string with mapping using mapResponse" in {
      postEcho
        .body(testBody)
        .mapResponseRight(_.length)
        .send()
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse.length)) }
    }

    "as string with mapping using mapWithHeaders" in {
      postEcho
        .body(testBody)
        .response(asStringAlways.mapWithMetadata { (b, h) => b + " " + h.contentType.getOrElse("") })
        .send()
        .toFuture()
        .map { response =>
          response.body should include(expectedPostEchoResponse)
          response.body should include("text/plain")
        }
    }

    "as a byte array" in {
      postEcho.body(testBody).response(asByteArray).send().toFuture().map { response =>
        val fc = new String(response.body.right.get, "UTF-8")
        fc should be(expectedPostEchoResponse)
      }
    }

    "as parameters" in {
      val params = List("a" -> "b", "c" -> "d", "e=" -> "&f")
      basicRequest
        .post(uri"$endpoint/echo/form_params/as_params")
        .body(params: _*)
        .response(asParams)
        .send()
        .toFuture()
        .map { response => response.body.right.map(_.toList) should be(Right(params)) }
    }

    "as string with response via metadata" in {
      val expectedStatus = StatusCode.BadRequest
      basicRequest
        .post(uri"$endpoint/echo/custom_status/${expectedStatus.code}")
        .body(testBody)
        .response(asStringAlways.mapWithMetadata((r, m) => if (m.code == expectedStatus) Right(r) else Left(r)))
        .send()
        .toFuture()
        .map { response =>
          response.body should be(Right(s"POST /echo/custom_status/${expectedStatus.code} $testBody"))
        }
    }

    "as error string with response via metadata" in {
      val unexpectedStatus = StatusCode.Ok
      basicRequest
        .post(uri"$endpoint/echo/custom_status/${unexpectedStatus.code}")
        .body(testBody)
        .response(asStringAlways.mapWithMetadata((r, m) => if (m.code == unexpectedStatus) Left(r) else Right(r)))
        .send()
        .toFuture()
        .map { response =>
          response.body should be(Left(s"POST /echo/custom_status/${unexpectedStatus.code} $testBody"))
        }
    }

    "as string, when the content type encoding is in quotes" in {
      basicRequest
        .post(uri"$endpoint/set_content_type_header_with_encoding_in_quotes")
        .body(testBody)
        .send()
        .toFuture()
        .map { response => response.body should be(Right(testBody)) }
    }
  }

  "parameters" - {
    "make a get request with parameters" in {
      basicRequest
        .get(uri"$endpoint/echo?p2=v2&p1=v1")
        .send()
        .toFuture()
        .map { response => response.body should be(Right("GET /echo p1=v1 p2=v2")) }
    }
  }

  "body" - {
    "post a string" in {
      postEcho.body(testBody).send().toFuture().map { response =>
        response.body should be(Right(expectedPostEchoResponse))
      }
    }

    "post a byte array" in {
      postEcho.body(testBodyBytes).send().toFuture().map { response =>
        response.body should be(Right(expectedPostEchoResponse))
      }
    }

    "post an input stream" in {
      postEcho
        .body(new ByteArrayInputStream(testBodyBytes))
        .send()
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse)) }
    }

    "post a byte buffer" in {
      postEcho
        .body(ByteBuffer.wrap(testBodyBytes))
        .send()
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse)) }
    }

    "post a readonly byte buffer" in {
      postEcho
        .body(ByteBuffer.wrap(testBodyBytes).asReadOnlyBuffer())
        .send()
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse)) }
    }

    "post form data" in {
      basicRequest
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a" -> "b", "c" -> "d")
        .send()
        .toFuture()
        .map { response => response.body should be(Right("a=b c=d")) }
    }

    "post form data with special characters" in {
      basicRequest
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a=" -> "/b", "c:" -> "/d")
        .send()
        .toFuture()
        .map { response => response.body should be(Right("a==/b c:=/d")) }
    }

    "post without a body" in {
      postEcho.send().toFuture().map { response => response.body should be(Right("POST /echo")) }
    }
  }

  protected def cacheControlHeaders: Set[String] = Set("no-cache", "max-age=1000")

  "headers" - {
    def getHeaders = basicRequest.get(uri"$endpoint/set_headers")

    "read response headers" in {
      getHeaders.response(sttpIgnore).send().toFuture().map { response =>
        response.headers should have length (4 + cacheControlHeaders.size).toLong
        response.headers("Cache-Control").toSet should be(cacheControlHeaders)
        response.header("Server").exists(_.startsWith("akka-http")) should be(true)
        response.contentType should be(Some("text/plain; charset=UTF-8"))
        response.contentLength should be(Some(2L))
      }
    }
  }

  "errors" - {
    "return 405 when method not allowed" in {
      basicRequest.post(uri"$endpoint/set_headers").send().toFuture().map { response =>
        response.code shouldBe StatusCode.MethodNotAllowed
        response.isClientError should be(true)
        response.body.isLeft should be(true)
      }
    }

    "return 404 when not found" in {
      basicRequest.get(uri"$endpoint/not/found").send().toFuture().map { response =>
        response.code shouldBe StatusCode.NotFound
        response.isClientError should be(true)
        response.body.isLeft should be(true)
      }
    }
  }

  "auth" - {
    def secureBasic = basicRequest.get(uri"$endpoint/secure_basic").response(asStringAlways)

    "return a 401 when authorization fails" in {
      val req = secureBasic
      req.send().toFuture().map { resp =>
        resp.code shouldBe StatusCode.Unauthorized
        resp.header("WWW-Authenticate") should be(Some("""Basic realm="test realm",charset=UTF-8"""))
      }
    }

    "perform basic authorization" in {
      val req = secureBasic.auth.basic("adam", "1234")
      req.send().toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body should be("Hello, adam!")
      }
    }
  }

  "compression" - {
    def compress = basicRequest.get(uri"$endpoint/compress").response(asStringAlways)
    val decompressedBody = "I'm compressed!"

    "decompress using the default accept encoding header" in {
      val req = compress
      req.send().toFuture().map { resp => resp.body should be(decompressedBody) }
    }

    "decompress using gzip" in {
      val req = compress.header("Accept-Encoding", "gzip", replaceExisting = true)
      req.send().toFuture().map { resp => resp.body should be(decompressedBody) }
    }

    "decompress using deflate" in {
      val req = compress.header("Accept-Encoding", "deflate", replaceExisting = true)
      req.send().toFuture().map { resp => resp.body should be(decompressedBody) }
    }

    "work despite providing an unsupported encoding" in {
      val req = compress.header("Accept-Encoding", "br", replaceExisting = true)
      req.send().toFuture().map { resp => resp.body should be(decompressedBody) }
    }

    "not attempt to decompress HEAD requests" in {
      val req = basicRequest.head(uri"$endpoint/compress")
      req.send().toFuture().map { resp => resp.code shouldBe StatusCode.Ok }
    }
  }

  // in JavaScript the only way to set the content type is to use a Blob which defaults the filename to 'blob'
  protected def multipartStringDefaultFileName: Option[String] = None
  protected def defaultFileName: String = multipartStringDefaultFileName match {
    case None       => ""
    case Some(name) => s" ($name)"
  }

  "multipart" - {
    def mp = basicRequest.post(uri"$endpoint/multipart").response(asStringAlways)

    "send a multipart message" in {
      val req = mp.multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
      req.send().toFuture().map { resp => resp.body should be(s"p1=v1$defaultFileName, p2=v2$defaultFileName") }
    }

    "send a multipart message with filenames" in {
      val req = mp.multipartBody(multipart("p1", "v1").fileName("f1"), multipart("p2", "v2").fileName("f2"))
      req.send().toFuture().map { resp => resp.body should be("p1=v1 (f1), p2=v2 (f2)") }
    }
  }

  protected def expectRedirectResponse(
      response: F[Response[String]],
      code: Int
  ): Future[Assertion] = {
    response.toFuture().map { resp =>
      resp.code shouldBe StatusCode(code)
      resp.history shouldBe Symbol("empty")
    }
  }

  "redirect" - {
    def r1 = basicRequest.post(uri"$endpoint/redirect/r1").response(asStringAlways)
    def r2 = basicRequest.post(uri"$endpoint/redirect/r2").response(asStringAlways)
    val r4response = "819"
    def loop = basicRequest.post(uri"$endpoint/redirect/loop").response(asStringAlways)

    "not redirect when redirects shouldn't be followed (temporary)" in {
      expectRedirectResponse(r1.followRedirects(false).send(), 307)
    }

    "not redirect when redirects shouldn't be followed (permanent)" in {
      expectRedirectResponse(r2.followRedirects(false).send(), 308)
    }

    "redirect when redirects should be followed" in {
      r2.send().toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body shouldBe r4response
      }
    }

    "redirect twice when redirects should be followed" in {
      r1.send().toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body shouldBe r4response
      }
    }

    "redirect when redirects should be followed, and the response is parsed" in {
      r2.response(asString).mapResponseRight(_.toInt).send().toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body shouldBe Right(r4response.toInt)
      }
    }

    "not redirect when maxRedirects is less than or equal to 0" in {
      expectRedirectResponse(loop.maxRedirects(-1).send(), 302)
    }
  }

  if (supportsRequestTimeout) {
    "timeout" - {
      "fail if read timeout is not big enough" in {
        val req = basicRequest
          .get(uri"$endpoint/timeout")
          .readTimeout(200.milliseconds)
          .response(asString)

        Future(req.send()).flatMap(_.toFuture()).failed.map { _ => succeed }
      }

      "not fail if read timeout is big enough" in {
        val req = basicRequest
          .get(uri"$endpoint/timeout")
          .readTimeout(5.seconds)
          .response(asString)

        req.send().toFuture().map { response => response.body should be(Right("Done")) }
      }
    }
  }

  "empty response" - {
    def emptyAnauthroizedResponseUri = uri"$endpoint/empty_unauthorized_response"
    def postEmptyResponse =
      basicRequest
        .post(emptyAnauthroizedResponseUri)
        .body("{}")
        .contentType("application/json")

    "parse an empty error response as empty string" in {
      postEmptyResponse.send().toFuture().map { response => response.body should be(Left("")) }
    }

    "in a head request" in {
      basicRequest.head(emptyAnauthroizedResponseUri).send().toFuture().map { response =>
        response.body should be(Left(""))
      }
    }
  }

  if (supportsSttpExceptions) {
    "exceptions" - {
      "connection exceptions - unknown host" in {
        val req = basicRequest
          .get(uri"http://no-such-domain-1234.com")
          .response(asString)

        Future(req.send()).flatMap(_.toFuture()).failed.map { e => e shouldBe a[SttpClientException.ConnectException] }
      }

      "connection exceptions - connection refused" in {
        val req = basicRequest
          .get(uri"http://localhost:1234")
          .response(asString)

        Future(req.send()).flatMap(_.toFuture()).failed.map { e => e shouldBe a[SttpClientException.ConnectException] }
      }

      "read exceptions - error" in {
        val req = basicRequest
          .get(uri"$endpoint/error")
          .response(asString)

        Future(req.send()).flatMap(_.toFuture()).failed.map { e => e shouldBe a[SttpClientException.ReadException] }
      }

      if (supportsRequestTimeout) {
        "read exceptions - timeout" in {
          val req = basicRequest
            .get(uri"$endpoint/timeout")
            .readTimeout(10.milliseconds)
            .response(asString)

          Future(req.send()).flatMap(_.toFuture()).failed.map { e => e shouldBe a[SttpClientException.ReadException] }
        }
      }
    }
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture()
    super.afterAll()
  }
}

object HttpTest {
  private val port = 51823
  val endpoint: String = s"http://localhost:$port"
  val wsEndpoint: String = s"ws://localhost:$port"
}