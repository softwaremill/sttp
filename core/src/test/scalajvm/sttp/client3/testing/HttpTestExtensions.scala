package sttp.client3.testing

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.{ZoneId, ZonedDateTime}
import sttp.client3._
import sttp.model.{Header, HeaderNames, StatusCode}

import scala.concurrent.Future
import HttpTest.endpoint
import org.scalatest.freespec.AsyncFreeSpecLike
import sttp.model.headers.CookieWithMeta

trait HttpTestExtensions[F[_]] extends AsyncFreeSpecLike { self: HttpTest[F] =>
  "cookies" - {
    "read response cookies" in {
      basicRequest
        .get(uri"$endpoint/cookies/set")
        .response(sttpIgnore)
        .send(backend)
        .toFuture()
        .map { response =>
          response.unsafeCookies should have length (3)
          response.unsafeCookies.toSet should be(
            Set(
              CookieWithMeta.unsafeApply("cookie1", "value1", secure = true, httpOnly = true, maxAge = Some(123L)),
              CookieWithMeta.unsafeApply("cookie2", "value2"),
              CookieWithMeta.unsafeApply("cookie3", "", domain = Some("xyz"), path = Some("a/b/c"))
            )
          )
        }
    }

    "read response cookies with the expires attribute" in {
      basicRequest
        .get(uri"$endpoint/cookies/set_with_expires")
        .response(sttpIgnore)
        .send(backend)
        .toFuture()
        .map { response =>
          response.unsafeCookies should have length (1)
          val c = response.unsafeCookies(0)

          c.name should be("c")
          c.value should be("v")
          c.expires.map(_.toEpochMilli) should be(
            Some(
              ZonedDateTime
                .of(1997, 12, 8, 12, 49, 12, 0, ZoneId.of("GMT"))
                .toInstant
                .toEpochMilli
            )
          )
        }
    }

    "received cookies should not be cached and sent back" in {
      basicRequest
        .get(uri"$endpoint/cookies/set")
        .response(sttpIgnore)
        .send(backend)
        .toFuture()
        .flatMap { _ =>
          basicRequest
            .get(uri"$endpoint/cookies/get_cookie2")
            .response(asStringAlways)
            .send(backend)
            .toFuture()
            .map { response => response.body shouldBe "no cookie" }
        }
    }
  }

  // browsers do not allow access to redirect responses
  "follow redirects" - {
    def r1 = basicRequest.post(uri"$endpoint/redirect/r1").response(asStringAlways)
    def r3 = basicRequest.post(uri"$endpoint/redirect/r3").response(asStringAlways)
    val r4response = "819"
    def loop = basicRequest.post(uri"$endpoint/redirect/loop")

    "keep a single history entry of redirect responses" in {
      r3.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body should be(r4response)
        resp.history should have size (1)
        resp.history(0).code shouldBe StatusCode.Found
      }
    }

    "keep whole history of redirect responses" in {
      r1.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body should be(r4response)
        resp.history should have size (3)
        resp.history(0).code shouldBe StatusCode.TemporaryRedirect
        resp.history(1).code shouldBe StatusCode.PermanentRedirect
        resp.history(2).code shouldBe StatusCode.Found
      }
    }

    "break redirect loops" in {
      // sync backends can throw exceptions when evaluating send(), before the toFuture() conversion
      Future(loop.send(backend).toFuture()).flatMap(identity).failed.map {
        case TooManyRedirectsException(_, redirects) =>
          redirects shouldBe FollowRedirectsBackend.MaxRedirects
        case e => fail(e)
      }
    }

    "break redirect loops after user-specified count" in {
      val maxRedirects = 10
      Future(loop.maxRedirects(maxRedirects).send(backend).toFuture()).flatMap(identity).failed.collect {
        case TooManyRedirectsException(_, redirects) =>
          redirects shouldBe maxRedirects
      }
    }

    val redirectToGetTestData = List(
      (301, false, "POSTx"),
      (302, false, "POSTx"),
      (303, false, "GET"),
      (307, false, "POSTx"),
      (308, false, "POSTx"),
      (301, true, "GET"),
      (302, true, "GET"),
      (303, true, "GET"),
      (307, true, "POSTx"),
      (308, true, "POSTx")
    )

    for ((statusCode, redirectToGet, expectedBody) <- redirectToGetTestData) yield {
      s"for $statusCode redirect, with redirect post to get = $redirectToGet, should return body $expectedBody" in {
        basicRequest
          .redirectToGet(redirectToGet)
          .body("x")
          .post(uri"$endpoint/redirect/get_after_post/r$statusCode")
          .response(asStringAlways)
          .send(backend)
          .toFuture()
          .map { resp => resp.body shouldBe expectedBody }
      }
    }

    "strip sensitive headers" - {
      val testData = List(
        Header(HeaderNames.Authorization, "secret"),
        Header(HeaderNames.Cookie, "A=B;"),
        Header(HeaderNames.SetCookie, "A=B")
      )

      for (header <- testData) yield {
        s"for $header redirect" in {
          basicRequest
            .get(uri"$endpoint/redirect/strip_sensitive_headers/r1")
            .header(header)
            .response(asStringAlways)
            .send(backend)
            .toFuture()
            .map { resp =>
              println(resp.body)
              resp.body should not include header.toString
            }
        }
      }
    }
  }

  // scalajs and scala native only support US_ASCII, ISO_8859_1, UTF_8, UTF_16BE, UTF_16LE, UTF_16
  "encoding" - {
    "read response body encoded using ISO-8859-2, as specified in the header, overriding the default" in {
      val req = basicRequest.get(uri"$endpoint/respond_with_iso_8859_2")

      req.send(backend).toFuture().map { response => response.body should be(Right("Żółć!")) }
    }
  }

  private def withTemporaryFile[T](content: Option[Array[Byte]])(f: File => Future[T]): Future[T] = {
    val file = Files.createTempFile("sttp", "sttp")
    val result = Future {
      content match {
        case None       => Files.deleteIfExists(file)
        case Some(data) => Files.write(file, data)
      }
    }.flatMap { _ => f(file.toFile) }

    result.onComplete(_ => Files.deleteIfExists(file))
    result
  }

  private def withTemporaryNonExistentFile[T](f: File => Future[T]): Future[T] = withTemporaryFile(None)(f)

  private def md5Hash(bytes: Array[Byte]): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes)
    val hash = md.digest()
    hash.map(0xff & _).map("%02x".format(_)).mkString
  }

  private def md5FileHash(file: File): Future[String] = {
    Future.successful {
      md5Hash(Files.readAllBytes(file.toPath))
    }
  }

  "body" - {
    "post a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        postEcho.body(f).send(backend).toFuture().map { response =>
          response.body should be(Right(expectedPostEchoResponse))
        }
      }
    }
  }

  "download file" - {
    "download a binary file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/binary").response(asFile(file))
        req.send(backend).toFuture().flatMap { resp =>
          md5FileHash(resp.body.right.get).map { _ shouldBe binaryFileMD5Hash }
        }
      }
    }

    "download a binary file using asFile, overwriting its current content" in {
      withTemporaryFile(Some(Array(1))) { file =>
        val req = basicRequest.get(uri"$endpoint/download/binary").response(asFile(file))
        req.send(backend).toFuture().flatMap { resp =>
          md5FileHash(resp.body.right.get).map { _ shouldBe binaryFileMD5Hash }
        }
      }
    }

    "download a text file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/text").response(asFile(file))
        req.send(backend).toFuture().flatMap { resp =>
          md5FileHash(resp.body.right.get).map { _ shouldBe textFileMD5Hash }
        }
      }
    }
  }

  if (supportsMultipart) {
    "multipart" - {
      def mp = basicRequest.post(uri"$endpoint/multipart")

      "send a multipart message with a file" in {
        withTemporaryFile(Some(testBodyBytes)) { f =>
          val req = mp.multipartBody(multipartFile("p1", f), multipart("p2", "v2"))
          req.send(backend).toFuture().map { resp =>
            resp.body should be(Right(s"p1=$testBody (${f.getName}), p2=v2$defaultFileName"))
          }
        }
      }

      "send a multipart message with custom file name" in {
        withTemporaryFile(Some(testBodyBytes)) { f =>
          val req = mp.multipartBody(multipartFile("p1", f).fileName("test.txt"))
          req.send(backend).toFuture().map { resp => resp.body should be(Right(s"p1=$testBody (test.txt)")) }
        }
      }
    }
  }

  "auth" - {
    "perform digest authorization" in {
      implicit val digestBackend: SttpBackend[F, Any] =
        new DigestAuthenticationBackend[F, Any](backend, () => "e5d93287aa8532c1f5df9e052fda4c38")
      val req = basicRequest.get(uri"$endpoint/secure_digest").response(asStringAlways).auth.digest("adam", "1234")
      digestBackend.send(req).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
      }
    }
  }
}
