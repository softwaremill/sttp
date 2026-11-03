package sttp.client4.testing

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.{ZoneId, ZonedDateTime}
import sttp.client4._
import sttp.model.StatusCode

import scala.concurrent.Future
import HttpTest.endpoint
import org.scalatest.freespec.AsyncFreeSpecLike
import sttp.client4.wrappers.DigestAuthenticationBackend
import sttp.model.headers.CookieWithMeta
import sttp.model.Encodings
import java.util.zip.GZIPInputStream
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

trait HttpTestExtensions[F[_]] extends AsyncFreeSpecLike { self: HttpTest[F] =>
  protected def supportsResponseAsInputStream = true

  "parse response" - {
    if (supportsResponseAsInputStream) {
      "as input stream" in {
        postEcho.body(testBody).response(asInputStreamAlways(_.readAllBytes())).send(backend).toFuture().map {
          response =>
            val allBytes = response.body
            val fc = new String(allBytes, "UTF-8")
            fc should be(expectedPostEchoResponse)
        }
      }

      "as input stream unsafe" in {
        postEcho.body(testBody).response(asInputStreamAlwaysUnsafe).send(backend).toFuture().map { response =>
          try {
            val allBytes = response.body.readAllBytes()
            val fc = new String(allBytes, "UTF-8")
            fc should be(expectedPostEchoResponse)
          } finally response.body.close()
        }
      }
    }
  }

  "cookies" - {
    "read response cookies" in {
      basicRequest
        .get(uri"$endpoint/cookies/set")
        .response(sttpIgnore)
        .send(backend)
        .toFuture()
        .map { response =>
          response.unsafeCookies should have length 3
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
          response.unsafeCookies should have length 1
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
            .map(response => response.body shouldBe "no cookie")
        }
    }
  }

  // scalajs and scala native only support US_ASCII, ISO_8859_1, UTF_8, UTF_16BE, UTF_16LE, UTF_16
  "encoding" - {
    "read response body encoded using ISO-8859-2, as specified in the header, overriding the default" in {
      val req = basicRequest.get(uri"$endpoint/respond_with_iso_8859_2")

      req.send(backend).toFuture().map(response => response.body should be(Right("Żółć!")))
    }
  }

  "compression" - {
    "should compress request body using gzip" in {
      val req = basicRequest
        .compressBody(Encodings.Gzip)
        .response(asByteArrayAlways)
        .post(uri"$endpoint/echo/exact")
        .body("I'm not compressed")
      req.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok

        val gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(resp.body))
        val decompressedBytes = gzipInputStream.readAllBytes()

        new String(decompressedBytes) shouldBe "I'm not compressed"
      }
    }

    "should compress request body using deflate" in {
      val req = basicRequest
        .compressBody(Encodings.Deflate)
        .response(asByteArrayAlways)
        .post(uri"$endpoint/echo/exact")
        .body("I'm not compressed")
      req.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok

        val inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(resp.body))
        val decompressedBytes = inflaterInputStream.readAllBytes()

        new String(decompressedBytes) shouldBe "I'm not compressed"
      }
    }

    "should compress a file-based request body using deflate" in {
      val testFileContent = "test file content"
      withTemporaryFile(Some(testFileContent.getBytes())) { file =>
        val req = basicRequest
          .compressBody(Encodings.Deflate)
          .response(asByteArrayAlways)
          .post(uri"$endpoint/echo/exact")
          .body(file)
        req.send(backend).toFuture().map { resp =>
          resp.code shouldBe StatusCode.Ok

          val inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(resp.body))
          val decompressedBytes = inflaterInputStream.readAllBytes()

          new String(decompressedBytes) shouldBe testFileContent
        }
      }
    }
  }

  private def withTemporaryFile[T](content: Option[Array[Byte]])(f: File => Future[T]): Future[T] = {
    val file = Files.createTempFile("sttp", "sttp")
    val result = Future {
      content match {
        case None       => Files.deleteIfExists(file)
        case Some(data) => Files.write(file, data)
      }
    }.flatMap(_ => f(file.toFile))

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

  private def md5FileHash(file: File): Future[String] =
    Future.successful {
      md5Hash(Files.readAllBytes(file.toPath))
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
          md5FileHash(resp.body.right.get).map(_ shouldBe binaryFileMD5Hash)
        }
      }
    }

    "download a binary file using asFile, overwriting its current content" in {
      withTemporaryFile(Some(Array(1))) { file =>
        val req = basicRequest.get(uri"$endpoint/download/binary").response(asFile(file))
        req.send(backend).toFuture().flatMap { resp =>
          md5FileHash(resp.body.right.get).map(_ shouldBe binaryFileMD5Hash)
        }
      }
    }

    "download a text file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/text").response(asFile(file))
        req.send(backend).toFuture().flatMap { resp =>
          md5FileHash(resp.body.right.get).map(_ shouldBe textFileMD5Hash)
        }
      }
    }

    if (self.supportsAutoDecompressionDisabling) {
      "should return compressed data" in {
        withTemporaryNonExistentFile { file =>
          val req = emptyRequest
            .get(uri"$endpoint/raw-gzip-file")
            .response(asFile(file))
            .acceptEncoding("gzip")
            .disableAutoDecompression
          req.send(backend).toFuture().flatMap { resp =>
            md5FileHash(resp.body.right.get).map {
              _ should be(gzipFileMD5hash)
            }
          }
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
          req.send(backend).toFuture().map(resp => resp.body should be(Right(s"p1=$testBody (test.txt)")))
        }
      }

      "send a multipart message with two files" in {
        withTemporaryFile(Some("file1:peach mario".getBytes())) { f1 =>
          withTemporaryFile(Some("file2:daisy luigi".getBytes())) { f2 =>
            mp
              .multipartBody(
                multipartFile("file1", f1).fileName("file1.txt"),
                multipartFile("file2", f2).fileName("file2.txt")
              )
              .response(asStringAlways)
              .send(backend)
              .toFuture()
              .map { r =>
                r.code shouldBe StatusCode.Ok
                r.body should include("file1:peach mario")
                r.body should include("file2:daisy luigi")
              }
          }
        }
      }
    }
  }

  "auth" - {
    "perform digest authorization" in {
      implicit val digestBackend: Backend[F] =
        DigestAuthenticationBackend(backend, () => "e5d93287aa8532c1f5df9e052fda4c38")
      val req = basicRequest.get(uri"$endpoint/secure_digest").response(asStringAlways).auth.digest("adam", "1234")
      req.send(digestBackend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
      }
    }
  }
}
