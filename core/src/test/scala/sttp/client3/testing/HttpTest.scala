package sttp.client3.testing

import org.scalatest._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.internal.{Iso88591, Utf8}
import sttp.client3.testing.HttpTest.endpoint
import sttp.client3.{Response, ResponseAs, SttpBackend, _}
import sttp.model.StatusCode
import sttp.monad.MonadError

import java.io.{ByteArrayInputStream, UnsupportedEncodingException}
import java.nio.ByteBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success, Try}

trait HttpTest[F[_]]
    extends AsyncFreeSpec
    with BeforeAndAfterAll
    with Matchers
    with ToFutureWrapper
    with OptionValues
    with HttpTestExtensions[F]
    with AsyncRetries {

  protected val binaryFileMD5Hash = "565370873a38d91f34a3091082e63933"
  protected val textFileMD5Hash = "b048a88ece8e4ec5eb386b8fc5006d13"

  val backend: SttpBackend[F, Any]
  implicit val convertToFuture: ConvertToFuture[F]

  // should only be implemented in supportsCancellation == true
  def timeoutToNone[T](t: F[T], timeoutMillis: Int): F[Option[T]]

  protected def postEcho: Request[Either[String, String], Any] = basicRequest.post(uri"$endpoint/echo")
  protected def postEchoExact: Request[Either[String, String], Any] = basicRequest.post(uri"$endpoint/echo/exact")
  protected val testBody = "this is the body"
  protected val testBodyBytes: Array[Byte] = testBody.getBytes("UTF-8")
  protected val testBodySignedBytes: Array[Byte] = Array[Byte](-1)
  protected val expectedPostEchoResponse = "POST /echo this is the body"
  protected val customEncoding = "custom"
  protected val customEncodedData = "custom-data"

  protected val sttpIgnore: ResponseAs[Unit, Any] = sttp.client3.ignore

  protected def supportsRequestTimeout = true
  protected def supportsSttpExceptions = true
  protected def supportsMultipart = true
  protected def supportsCustomMultipartContentType = true
  protected def supportsCustomMultipartEncoding = true
  protected def supportsCustomContentEncoding = false
  protected def throwsExceptionOnUnsupportedEncoding = true
  protected def supportsHostHeaderOverride = true
  protected def supportsCancellation = true

  "parse response" - {
    "as string" in {
      postEcho.body(testBody).send(backend).toFuture().map { response =>
        response.body should be(Right(expectedPostEchoResponse))
      }
    }

    "as string with utf-8 characters" in {
      postEcho.body("this is the body😀").send(backend).toFuture().map { response =>
        response.body should be(Right("POST /echo this is the body😀"))
      }
    }

    "as string with mapping using map" in {
      postEcho
        .body(testBody)
        .response(asString.mapRight(_.length))
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse.length)) }
    }

    "as string with mapping using mapResponse" in {
      postEcho
        .body(testBody)
        .mapResponseRight(_.length)
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse.length)) }
    }

    "as string with mapping using mapWithHeaders" in {
      postEcho
        .body(testBody)
        .response(asStringAlways.mapWithMetadata { (b, h) => b + " " + h.contentType.getOrElse("") })
        .send(backend)
        .toFuture()
        .map { response =>
          response.body should include(expectedPostEchoResponse)
          response.body should include("text/plain")
        }
    }

    "as a byte array" in {
      postEcho.body(testBody).response(asByteArray).send(backend).toFuture().map { response =>
        val fc = new String(response.body.right.get, "UTF-8")
        fc should be(expectedPostEchoResponse)
      }
    }

    "as a byte array exact" in {
      postEchoExact.body(testBodySignedBytes).response(asByteArrayAlways).send(backend).toFuture().map { response =>
        response.body should be(testBodySignedBytes)
      }
    }

    "as parameters" in {
      val params = List("a" -> "b", "c" -> "d", "e=" -> "&f")
      basicRequest
        .post(uri"$endpoint/echo/form_params/as_params")
        .body(params: _*)
        .response(asParams)
        .send(backend)
        .toFuture()
        .map { response => response.body.right.map(_.toList) should be(Right(params)) }
    }

    "as string with response via metadata" in {
      val expectedStatus = StatusCode.BadRequest
      basicRequest
        .post(uri"$endpoint/echo/custom_status/${expectedStatus.code}")
        .body(testBody)
        .response(asStringAlways.mapWithMetadata((r, m) => if (m.code == expectedStatus) Right(r) else Left(r)))
        .send(backend)
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
        .send(backend)
        .toFuture()
        .map { response =>
          response.body should be(Left(s"POST /echo/custom_status/${unexpectedStatus.code} $testBody"))
        }
    }

    "as string, when the content type encoding is in quotes" in {
      basicRequest
        .post(uri"$endpoint/set_content_type_header_with_encoding_in_quotes")
        .body(testBody)
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right(testBody)) }
    }

    "as both string and mapped string" in {
      postEcho
        .body(testBody)
        .response(asBoth(asStringAlways, asByteArray.mapRight(_.length)))
        .send(backend)
        .toFuture()
        .map { response =>
          response.body shouldBe ((expectedPostEchoResponse, Right(expectedPostEchoResponse.getBytes.length)))
        }
    }
  }

  "parameters" - {
    "make a get request with parameters" in {
      basicRequest
        .get(uri"$endpoint/echo?p2=v2&p1=v1")
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right("GET /echo p1=v1 p2=v2")) }
    }
  }

  "body" - {
    "post a string" in {
      postEcho.body(testBody).send(backend).toFuture().map { response =>
        response.body should be(Right(expectedPostEchoResponse))
      }
    }

    "post a byte array" in {
      postEcho.body(testBodyBytes).send(backend).toFuture().map { response =>
        response.body should be(Right(expectedPostEchoResponse))
      }
    }

    "post an input stream" in {
      postEcho
        .body(new ByteArrayInputStream(testBodyBytes))
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse)) }
    }

    "post a byte buffer" in {
      postEcho
        .body(ByteBuffer.wrap(testBodyBytes))
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse)) }
    }

    "post a byte buffer with a capacity higher than the limit" in {
      val b = ByteBuffer.allocate(100)
      b.put(testBodyBytes)
      b.flip()

      postEcho
        .body(b)
        .response(asByteArrayAlways)
        .send(backend)
        .toFuture()
        .map { response =>
          response.body shouldBe expectedPostEchoResponse.getBytes("UTF-8")
        }
    }

    "post a readonly byte buffer" in {
      postEcho
        .body(ByteBuffer.wrap(testBodyBytes).asReadOnlyBuffer())
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right(expectedPostEchoResponse)) }
    }

    "post form data" in {
      basicRequest
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a" -> "b", "c" -> "d")
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right("a=b c=d")) }
    }

    "post form data with special characters" in {
      basicRequest
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a=" -> "/b", "c:" -> "/d")
        .send(backend)
        .toFuture()
        .map { response => response.body should be(Right("a==/b c:=/d")) }
    }

    "post without a body" in {
      postEcho.send(backend).toFuture().map { response => response.body should be(Right("POST /echo")) }
    }
  }

  protected def cacheControlHeaders: Set[String] = Set("no-cache", "max-age=1000")

  "headers" - {
    def getHeaders = basicRequest.get(uri"$endpoint/set_headers")

    "read response headers" in {
      getHeaders.response(sttpIgnore).send(backend).toFuture().map { response =>
        response.headers should have length (4 + cacheControlHeaders.size).toLong
        response.headers("Cache-Control").toSet should be(cacheControlHeaders)
        response.header("Server").exists(_.startsWith("akka-http")) should be(true)
        response.contentType should be(Some("text/plain; charset=UTF-8"))
        response.contentLength should be(Some(2L))
      }
    }

    // https://github.com/softwaremill/sttp/issues/676
    "include a header once when sent effect is used multiple times" in {
      val request = basicRequest.get(uri"$endpoint/echo/headers").header("x-foo", "bar").response(asStringAlways)
      val sent = request.send(backend)
      backend.responseMonad.flatMap(sent)(_ => sent).toFuture().map(_.body).map { body =>
        body should include("x-foo->bar")
        body.split(",").toList.count(_ == "x-foo->bar") shouldBe 1
      }
    }

    if (supportsHostHeaderOverride) {
      "should not send the URL's hostname as the host header" in {
        basicRequest
          .get(uri"$endpoint/echo/headers")
          .header("Host", "test.com")
          .response(asStringAlways)
          .send(backend)
          .toFuture()
          .map { response =>
            response.body should include("Host->test.com")
            response.body should not include "Host->localhost"
          }
      }
    }
  }

  "errors" - {
    "return 405 when method not allowed" in {
      basicRequest.post(uri"$endpoint/set_headers").send(backend).toFuture().map { response =>
        response.code shouldBe StatusCode.MethodNotAllowed
        response.isClientError should be(true)
        response.body.isLeft should be(true)
      }
    }

    "return 404 when not found" in {
      basicRequest.get(uri"$endpoint/not/found").send(backend).toFuture().map { response =>
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
      req.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Unauthorized
        resp.header("WWW-Authenticate") should be(Some("""Basic realm="test realm",charset=UTF-8"""))
      }
    }

    "perform basic authorization" in {
      val req = secureBasic.auth.basic("adam", "1234")
      req.send(backend).toFuture().map { resp =>
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
      req.send(backend).toFuture().map { resp => resp.body should be(decompressedBody) }
    }

    "decompress using gzip" in {
      val req = compress.acceptEncoding("gzip")
      req.send(backend).toFuture().map { resp => resp.body should be(decompressedBody) }
    }

    "decompress using deflate" in {
      val req = compress.acceptEncoding("deflate")
      req.send(backend).toFuture().map { resp => resp.body should be(decompressedBody) }
    }

    "work despite providing an unsupported encoding" in {
      val req = compress.acceptEncoding("br")
      req.send(backend).toFuture().map { resp => resp.body should be(decompressedBody) }
    }

    "not attempt to decompress HEAD requests" in {
      val req = basicRequest.head(uri"$endpoint/compress")
      req.send(backend).toFuture().map { resp => resp.code shouldBe StatusCode.Ok }
    }

    if (supportsCustomContentEncoding) {
      "decompress using custom content encoding" in {
        val req =
          basicRequest.get(uri"$endpoint/compress-custom").acceptEncoding(customEncoding).response(asStringAlways)
        req.send(backend).toFuture().map { resp => resp.body should be(customEncodedData) }
      }
    }

    if (supportsSttpExceptions && throwsExceptionOnUnsupportedEncoding) {
      "should throw exception when response contains unsupported encoding" in {
        val req = basicRequest.get(uri"$endpoint/compress-unsupported").response(asStringAlways)
        Future(req.send(backend)).flatMap(_.toFuture()).failed.map { e =>
          e shouldBe a[SttpClientException.ReadException]
          e.getCause shouldBe an[UnsupportedEncodingException]
        }
      }
    }
  }

  // in JavaScript the only way to set the content type is to use a Blob which defaults the filename to 'blob'
  protected def multipartStringDefaultFileName: Option[String] = None
  protected def defaultFileName: String =
    multipartStringDefaultFileName match {
      case None       => ""
      case Some(name) => s" ($name)"
    }

  if (supportsMultipart) {
    "multipart" - {
      def mp = basicRequest.post(uri"$endpoint/multipart").response(asStringAlways)

      "send a multipart message" in {
        val req = mp.multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
        req.send(backend).toFuture().map { resp =>
          resp.body should be(s"p1=v1$defaultFileName, p2=v2$defaultFileName")
        }
      }

      "send a multipart message with an explicitly set content type header" in {
        val req = mp
          .multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
          .contentType("multipart/form-data")
        req.send(backend).toFuture().map { resp =>
          resp.body should be(s"p1=v1$defaultFileName, p2=v2$defaultFileName")
        }
      }

      "send a multipart message with filenames" in {
        val req = mp.multipartBody(multipart("p1", "v1").fileName("f1"), multipart("p2", "v2").fileName("f2"))
        req.send(backend).toFuture().map { resp => resp.body should be("p1=v1 (f1), p2=v2 (f2)") }
      }

      "send a multipart message with binary data and filename" in {
        val binaryPart = {
          multipart("p1", "v1".getBytes)
            .fileName("f1")
        }
        val req = mp.multipartBody(binaryPart)
        req.send(backend).toFuture().map { resp =>
          resp.body should include("f1")
          resp.body should include("v1")
        }
      }

      if (supportsCustomMultipartEncoding) {
        "send a multipart message containing body parts with explicitly set encodings" in {

          val utf8Char = "\u00f6" //ö
          val replacementChar = "\ufffd" //�

          val req = basicRequest
            .post(uri"$endpoint/multipart/content_type")
            .response(asStringAlways)
            .multipartBody(
              multipart("p1", utf8Char, Utf8),
              multipart("p2", utf8Char, Iso88591)
            )

          req.send(backend).toFuture().map { resp =>
            resp.body should be(
              //replacementChar should be used in place of utf8Char for the part with Iso88591 encoding
              s"p1=$utf8Char$defaultFileName content-type: text/plain; charset=UTF-8, p2=$replacementChar$defaultFileName content-type: text/plain; charset=ISO-8859-1"
            )
          }
        }
      }

      if (supportsCustomMultipartContentType) {
        "send a multipart message with custom content type" in {
          val req = basicRequest
            .post(uri"$endpoint/multipart/other")
            .response(asStringAlways)
            .multipartBody(multipart("p1", "v1"))
            .contentType("multipart/mixed")
          req.send(backend).toFuture().map { resp =>
            resp.body should include("multipart/mixed")
            resp.body should include("v1")
          }
        }
      }
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
      expectRedirectResponse(r1.followRedirects(false).send(backend), 307)
    }

    "not redirect when redirects shouldn't be followed (permanent)" in {
      expectRedirectResponse(r2.followRedirects(false).send(backend), 308)
    }

    "redirect when redirects should be followed" in {
      r2.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body shouldBe r4response
      }
    }

    "redirect twice when redirects should be followed" in {
      r1.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body shouldBe r4response
      }
    }

    "redirect when redirects should be followed, and the response is parsed" in {
      r2.response(asString).mapResponseRight(_.toInt).send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body shouldBe Right(r4response.toInt)
      }
    }

    "redirect to a relative url" in {
      basicRequest.post(uri"$endpoint/redirect/relative").response(asStringAlways).send(backend).toFuture().map {
        resp =>
          resp.code shouldBe StatusCode.Ok
          resp.body shouldBe r4response
      }
    }

    "not redirect when maxRedirects is less than or equal to 0" in {
      expectRedirectResponse(loop.maxRedirects(-1).send(backend), 302)
    }
  }

  if (supportsRequestTimeout) {
    "timeout" - {
      "fail if read timeout is not big enough" in {
        val req = basicRequest
          .get(uri"$endpoint/timeout")
          .readTimeout(200.milliseconds)
          .response(asString)

        Future(req.send(backend)).flatMap(_.toFuture()).failed.map { _ => succeed }
      }

      "not fail if read timeout is big enough" in {
        val req = basicRequest
          .get(uri"$endpoint/timeout")
          .readTimeout(5.seconds)
          .response(asString)

        req.send(backend).toFuture().map { response => response.body should be(Right("Done")) }
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
      postEmptyResponse.send(backend).toFuture().map { response => response.body should be(Left("")) }
    }

    "in a head request" in {
      basicRequest.head(emptyAnauthroizedResponseUri).send(backend).toFuture().map { response =>
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

        Future(req.send(backend)).flatMap(_.toFuture()).failed.map { e =>
          e shouldBe a[SttpClientException.ConnectException]
        }
      }

      "connection exceptions - connection refused" in {
        retry(10) {
          val portToTry = Random.nextInt(2048) + 49152
          val req = basicRequest
            .get(uri"http://localhost:$portToTry")
            .response(asString)

          Future(req.send(backend)).flatMap(_.toFuture()).failed.map { e =>
            info(s"Sending request using port $portToTry failed", Some(e))
            e shouldBe a[SttpClientException.ConnectException]
          }
        }
      }

      "read exceptions - error" in {
        val req = basicRequest
          .get(uri"$endpoint/error")
          .response(asString)

        Future(req.send(backend)).flatMap(_.toFuture()).failed.map { e =>
          e shouldBe a[SttpClientException.ReadException]
        }
      }

      if (supportsRequestTimeout) {
        "read exceptions - timeout" in {
          val req = basicRequest
            .get(uri"$endpoint/timeout")
            .readTimeout(10.milliseconds)
            .response(asString)

          Future(req.send(backend)).flatMap(_.toFuture()).failed.map { e =>
            e shouldBe a[SttpClientException.ReadException]
          }
        }
      }
    }
  }

  if (supportsCancellation) {
    "cancel" - {
      "a request in progress" in {
        implicit val monad: MonadError[F] = backend.responseMonad
        import sttp.monad.syntax._

        val req = basicRequest
          .get(uri"$endpoint/timeout")
          .response(asString)

        val now = monad.eval(System.currentTimeMillis())

        convertToFuture.toFuture(
          now
            .flatMap { start =>
              timeoutToNone(req.send(backend), 100)
                .map { r =>
                  (System.currentTimeMillis() - start) should be < 2000L
                  r shouldBe None
                }
            }
        )
      }
    }
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture()
    super.afterAll()
  }

  private def retry[T](n: Int)(f: => Future[T]): Future[T] = {
    Try(f) match {
      case Failure(exception) => if (n == 0) Future.failed(exception) else retry(n - 1)(f)
      case Success(value) =>
        value.recoverWith {
          case e if n > 0 =>
            info(s"Failed with: ${e.getMessage}, retrying ($n).", Some(e))
            retry(n - 1)(f)
        }
    }
  }
}

object HttpTest {
  private val port = 51823
  val endpoint: String = s"http://localhost:$port"
  val wsEndpoint: String = s"ws://localhost:$port"
}
