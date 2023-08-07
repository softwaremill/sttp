package sttp.client4.testing

import org.scalatest.freespec.AnyFreeSpecLike
import sttp.client4._
import sttp.client4.wrappers.{FollowRedirectsBackend, TooManyRedirectsException}
import sttp.model.{Header, StatusCode}

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

trait SyncHttpTestExtensions extends AnyFreeSpecLike {
  self: SyncHttpTest =>

  // browsers do not allow access to redirect responses
  "follow redirects" - {
    def r1 = basicRequest.post(uri"$endpoint/redirect/r1").response(asStringAlways)
    def r3 = basicRequest.post(uri"$endpoint/redirect/r3").response(asStringAlways)
    val r4response = "819"
    def loop = basicRequest.post(uri"$endpoint/redirect/loop").response(asStringAlways)

    "keep a single history entry of redirect responses" in {
      val resp = r3.send(backend)
      resp.code shouldBe StatusCode.Ok
      resp.body should be(r4response)
      resp.history should have size 1
      resp.history(0).code shouldBe StatusCode.Found
    }

    "keep whole history of redirect responses" in {
      val resp = r1.send(backend)
      resp.code shouldBe StatusCode.Ok
      resp.body should be(r4response)
      resp.history should have size 3
      resp.history(0).code shouldBe StatusCode.TemporaryRedirect
      resp.history(1).code shouldBe StatusCode.PermanentRedirect
      resp.history(2).code shouldBe StatusCode.Found
    }

    "break redirect loops" in {
      intercept[TooManyRedirectsException] {
        loop.send(backend)
      }.redirects shouldBe FollowRedirectsBackend.MaxRedirects
    }

    "break redirect loops after user-specified count" in {
      val maxRedirects = 10
      intercept[TooManyRedirectsException] {
        loop.maxRedirects(maxRedirects).send(backend)
      }.redirects shouldBe maxRedirects
    }
  }

  private def withTemporaryFile[T](content: Option[Array[Byte]])(f: File => T): T = {
    val file = Files.createTempFile("sttp", "sttp")
    content match {
      case None       => Files.deleteIfExists(file)
      case Some(data) => Files.write(file, data)
    }
    val result = f(file.toFile)
    Files.deleteIfExists(file)
    result
  }

  // TODO: it will return empty value becasue there is a bug in scala native
  private def md5Hash(bytes: Array[Byte]): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes, 0, bytes.length)
    val hash = md.digest()
    hash.map(0xff & _).map("%02x".format(_)).mkString
  }

  private def md5FileHash(file: File): String = md5Hash(Files.readAllBytes(file.toPath))

  private def withTemporaryNonExistentFile[T](f: File => T): T = withTemporaryFile(None)(f)

  "body" - {
    "post a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        val response = postEcho.body(f).send(backend)
        response.body should be(Right(expectedPostEchoResponse))
      }
    }
  }

  "download file" - {

    "download a binary file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req: Request[Either[String, File]] =
          basicRequest.get(uri"$endpoint/download/binary").response(asFile(file))
        val resp: Response[Either[String, File]] = req.send(backend)
        val body = resp.body.right.get
        resp.headers should contain only Header.contentLength(body.length())
        body.toPath shouldBe file.toPath
      }
    }

    "download a text file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req: Request[Either[String, File]] =
          basicRequest.get(uri"$endpoint/download/text").response(asFile(file))
        val resp: Response[Either[String, File]] = req.send(backend)
        val body = resp.body.right.get
        resp.headers should contain only Header.contentLength(body.length())
        body.toPath shouldBe file.toPath
      }
    }
  }

  "multipart" - {
    def mp = basicRequest.post(uri"$endpoint/multipart")

    "send a multipart message with a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        val req = mp.multipartBody(multipartFile("p1", f), multipart("p2", "v2"))
        val resp = req.send(backend)
        resp.body should be(Right(s"p1=$testBody (${f.getName}), p2=v2$defaultFileName"))
      }
    }
  }
}
