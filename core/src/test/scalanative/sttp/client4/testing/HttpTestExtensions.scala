package sttp.client4.testing

import org.scalatest.freespec.AsyncFreeSpecLike
import sttp.client4._
import sttp.client4.internal.CryptoMd5
import sttp.client4.testing.HttpTest.endpoint
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.model.StatusCode

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer
import java.nio.file.Files
import scala.concurrent.Future

trait HttpTestExtensions[F[_]] extends AsyncFreeSpecLike with AsyncExecutionContext { self: HttpTest[F] =>

  "follow redirects" - {
    def r1 = basicRequest.post(uri"$endpoint/redirect/r1").response(asStringAlways)
    def r3 = basicRequest.post(uri"$endpoint/redirect/r3").response(asStringAlways)
    val r4response = "819"
    def loop = basicRequest.post(uri"$endpoint/redirect/loop")

    "keep a single history entry of redirect responses" in {
      r3.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body should be(r4response)
        resp.history should have size 1
        resp.history(0).code shouldBe StatusCode.Found
      }
    }

    "keep whole history of redirect responses" in {
      r1.send(backend).toFuture().map { resp =>
        resp.code shouldBe StatusCode.Ok
        resp.body should be(r4response)
        resp.history should have size 3
        resp.history(0).code shouldBe StatusCode.TemporaryRedirect
        resp.history(1).code shouldBe StatusCode.PermanentRedirect
        resp.history(2).code shouldBe StatusCode.Found
      }
    }

    "break redirect loops" in {
      Future(loop.send(backend)).flatMap(_.toFuture()).failed.map {
        case e: SttpClientException.TooManyRedirectsException =>
          e.redirects shouldBe FollowRedirectsBackend.MaxRedirects
        case e => fail(e)
      }
    }

    "break redirect loops after user-specified count" in {
      val maxRedirects = 10
      Future(loop.maxRedirects(maxRedirects).send(backend)).flatMap(_.toFuture()).failed.map {
        case e: SttpClientException.TooManyRedirectsException => e.redirects shouldBe maxRedirects
        case e                                                => fail(e)
      }
    }
  }

  "body" - {
    def putEcho = basicRequest.put(uri"$endpoint/echo")
    val expectedPutEchoResponse = "PUT /echo this is the body"

    "post a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        postEcho.body(f).send(backend).toFuture().map { response =>
          response.body should be(Right(expectedPostEchoResponse))
        }
      }
    }

    "put a string" in {
      putEcho.body(testBody).send(backend).toFuture().map(_.body should be(Right(expectedPutEchoResponse)))
    }

    "put a byte array" in {
      putEcho.body(testBodyBytes).send(backend).toFuture().map(_.body should be(Right(expectedPutEchoResponse)))
    }

    "put an input stream" in {
      putEcho
        .body(new ByteArrayInputStream(testBodyBytes))
        .send(backend)
        .toFuture()
        .map(_.body should be(Right(expectedPutEchoResponse)))
    }

    "put a byte buffer" in {
      putEcho
        .body(ByteBuffer.wrap(testBodyBytes))
        .send(backend)
        .toFuture()
        .map(_.body should be(Right(expectedPutEchoResponse)))
    }

    "put form data" in {
      basicRequest
        .put(uri"$endpoint/echo/form_params/as_string")
        .body("a" -> "b", "c" -> "d")
        .send(backend)
        .toFuture()
        .map(_.body should be(Right("a=b c=d")))
    }

    "put form data with special characters" in {
      basicRequest
        .put(uri"$endpoint/echo/form_params/as_string")
        .body("a=" -> "/b", "c:" -> "/d")
        .send(backend)
        .toFuture()
        .map(_.body should be(Right("a==/b c:=/d")))
    }

    "put without a body" in {
      putEcho.send(backend).toFuture().map(_.body should be(Right("PUT /echo")))
    }
  }

  "download file" - {
    "download a binary file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/binary").response(asFile(file))
        req.send(backend).toFuture().map(resp => md5FileHash(resp.body.right.get) shouldBe binaryFileMD5Hash)
      }
    }

    "download a binary file using asFile, overwriting its current content" in {
      withTemporaryFile(Some(Array(1))) { file =>
        val req = basicRequest.get(uri"$endpoint/download/binary").response(asFile(file))
        req.send(backend).toFuture().map(resp => md5FileHash(resp.body.right.get) shouldBe binaryFileMD5Hash)
      }
    }

    "download a text file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/text").response(asFile(file))
        req.send(backend).toFuture().map(resp => md5FileHash(resp.body.right.get) shouldBe textFileMD5Hash)
      }
    }
  }

  "multipart with files" - {
    "send a multipart message with a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        basicRequest
          .post(uri"$endpoint/multipart")
          .multipartBody(multipartFile("p1", f), multipart("p2", "v2"))
          .send(backend)
          .toFuture()
          .map(_.body should be(Right(s"p1=$testBody (${f.getName}), p2=v2$defaultFileName")))
      }
    }
  }

  "exceptions when streaming" - {
    "connection exceptions - connection refused, when streaming the response" in {
      val req = basicRequest.get(uri"http://localhost:7918").response(asInputStreamAlways(_ => ()))
      Future(req.send(backend)).flatMap(_.toFuture()).failed.map { e =>
        e shouldBe a[SttpClientException.ConnectException]
      }
    }
  }

  private def withTemporaryFile[T](content: Option[Array[Byte]])(f: File => Future[T]): Future[T] = {
    val file = Files.createTempFile("sttp", "sttp")
    content match {
      case None       => Files.deleteIfExists(file)
      case Some(data) => Files.write(file, data)
    }
    val result = Future.unit.flatMap(_ => f(file.toFile))
    result.onComplete(_ => Files.deleteIfExists(file))
    result
  }

  private def withTemporaryNonExistentFile[T](f: File => Future[T]): Future[T] = withTemporaryFile(None)(f)

  // java.security.MessageDigest doesn't work on Native
  private def md5FileHash(file: File): String =
    CryptoMd5.digest(Files.readAllBytes(file.toPath)).map(0xff & _).map("%02x".format(_)).mkString
}
