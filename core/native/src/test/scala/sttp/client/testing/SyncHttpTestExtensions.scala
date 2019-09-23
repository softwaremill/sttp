package sttp.client.testing

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

import sttp.client._

trait SyncHttpTestExtensions {
  self: SyncHttpTest =>

  // browsers do not allow access to redirect responses
  "follow redirects" - {
    def r1 = basicRequest.post(uri"$endpoint/redirect/r1").response(asStringAlways)
    def r3 = basicRequest.post(uri"$endpoint/redirect/r3").response(asStringAlways)
    val r4response = "819"
    def loop = basicRequest.post(uri"$endpoint/redirect/loop").response(asStringAlways)

    "keep a single history entry of redirect responses" in {
      val resp = r3.send()
      resp.code should be(200)
      resp.body should be(r4response)
      resp.history should have size (1)
      resp.history(0).code should be(302)
    }

    "keep whole history of redirect responses" in {
      val resp = r1.send()
      resp.code should be(200)
      resp.body should be(r4response)
      resp.history should have size (3)
      resp.history(0).code should be(307)
      resp.history(1).code should be(308)
      resp.history(2).code should be(302)
    }

    "break redirect loops" in {
      intercept[TooManyRedirectsException] {
        loop.send()
      }.redirects shouldBe FollowRedirectsBackend.MaxRedirects
    }

    "break redirect loops after user-specified count" in {
      val maxRedirects = 10
      intercept[TooManyRedirectsException] {
        loop.maxRedirects(maxRedirects).send()
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

  private def md5Hash(bytes: Array[Byte]): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes, 0, bytes.length)
    val hash = md.digest()
    hash.map(0xFF & _).map("%02x".format(_)).mkString
  }

  private def md5FileHash(file: File): String = md5Hash(Files.readAllBytes(file.toPath))

  private def withTemporaryNonExistentFile[T](f: File => T): T = withTemporaryFile(None)(f)

  "body" - {
    "post a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        val response = postEcho.body(f).send()
        response.body should be(Right(expectedPostEchoResponse))
      }
    }
  }

  "download file" - {

    "download a binary file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/binary").response(asFile(file))
        val resp = req.send()
        md5FileHash(resp.body.right.get).map { _ shouldBe binaryFileMD5Hash }
      }
    }

    "download a text file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = basicRequest.get(uri"$endpoint/download/text").response(asFile(file))
        val resp = req.send()
        md5FileHash(resp.body.right.get).map { _ shouldBe textFileMD5Hash }
      }
    }
  }

  "download file overwrite" - {
    "fail when file exists and overwrite flag is false" in {
      withTemporaryFile(Some(testBodyBytes)) { file =>
        val req = basicRequest.get(uri"$endpoint/download/text").response(asFile(file))

        try {
          req.send()
          fail("IOException should be thrown")
        } catch {
          case e: Exception => e.getMessage shouldBe s"File ${file.getAbsolutePath} exists - overwriting prohibited"
        }
      }
    }

    "not fail when file exists and overwrite flag is true" in {
      withTemporaryFile(Some(testBodyBytes)) { file =>
        val req = basicRequest
          .get(uri"$endpoint/download/text")
          .response(asFile(file, overwrite = true))
        val resp = req.send()
        md5FileHash(resp.body.right.get).map { _ shouldBe textFileMD5Hash }
      }
    }
  }

  "multipart" - {
    def mp = basicRequest.post(uri"$endpoint/multipart")

    "send a multipart message with a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        val req = mp.multipartBody(multipartFile("p1", f), multipart("p2", "v2"))
        val resp = req.send()
        resp.body should be(Right(s"p1=$testBody (${f.getName}), p2=v2$defaultFileName"))
      }
    }
  }
}
