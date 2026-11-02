package sttp.client4.testing

import org.scalatest.freespec.AsyncFreeSpecLike
import sttp.client4._
import sttp.client4.internal.CryptoMd5
import sttp.client4.testing.HttpTest.endpoint

import java.io.File
import java.nio.file.Files
import scala.concurrent.Future

trait HttpTestExtensions[F[_]] extends AsyncFreeSpecLike with AsyncExecutionContext { self: HttpTest[F] =>

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
