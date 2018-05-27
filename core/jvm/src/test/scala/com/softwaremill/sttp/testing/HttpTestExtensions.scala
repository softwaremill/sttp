package com.softwaremill.sttp.testing

import java.io.IOException
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.time.{ZoneId, ZonedDateTime}

import com.softwaremill.sttp._
import com.softwaremill.sttp.internal.SttpFile

import scala.concurrent.Future
import scala.language.higherKinds

object HttpTestExtensions {

  private val textWithSpecialCharacters = "Żółć!"

}

trait HttpTestExtensions[R[_]] extends TestHttpServer { self: HttpTest[R] =>
  import HttpTestExtensions._

  override protected def withTemporaryFile[T](content: Option[Array[Byte]])(f: SttpFile => Future[T]): Future[T] = {
    val file = Files.createTempFile("sttp", "sttp")
    val result = Future {
      content match {
        case None       => Files.deleteIfExists(file)
        case Some(data) => Files.write(file, data)
      }
    }.flatMap { _ =>
      f(SttpFile.fromPath(file))
    }

    result.onComplete(_ => Files.deleteIfExists(file))
    result
  }

  override protected def md5Hash(bytes: Array[Byte]): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(bytes)
    val hash = md.digest()
    hash.map(0xFF & _).map("%02x".format(_)).mkString
  }

  override protected def md5FileHash(file: SttpFile): Future[String] = {
    Future.successful {
      md5Hash(Files.readAllBytes(file.toPath))
    }
  }

  "cookies" - {
    "read response cookies" in {
      sttp
        .get(uri"$endpoint/set_cookies")
        .response(sttpIgnore)
        .send()
        .toFuture()
        .map { response =>
          response.cookies should have length (3)
          response.cookies.toSet should be(Set(
            Cookie("cookie1", "value1", secure = true, httpOnly = true, maxAge = Some(123L)),
            Cookie("cookie2", "value2"),
            Cookie("cookie3", "", domain = Some("xyz"), path = Some("a/b/c"))
          ))
        }
    }

    "read response cookies with the expires attribute" in {
      sttp
        .get(uri"$endpoint/set_cookies/with_expires")
        .response(sttpIgnore)
        .send()
        .toFuture()
        .map { response =>
          response.cookies should have length (1)
          val c = response.cookies(0)

          c.name should be("c")
          c.value should be("v")
          c.expires.map(_.toInstant.toEpochMilli) should be(
            Some(
              ZonedDateTime
                .of(1997, 12, 8, 12, 49, 12, 0, ZoneId.of("GMT"))
                .toInstant
                .toEpochMilli
            ))
        }
    }
  }

  "download file overwrite" - {
    "fail at trying to save file to a restricted location" in {
      val path = Paths.get("/").resolve("textfile.txt")
      val req = sttp.get(uri"$endpoint/download/text").response(asFile(path.toFile))
      Future(req.send()).flatMap(_.toFuture()).failed.collect {
        case caught: IOException => caught.getMessage shouldBe "Permission denied"
      }
    }

    "fail when file exists and overwrite flag is false" in {
      withTemporaryFile(Some(testBodyBytes)) { file =>
        val req = sttp.get(uri"$endpoint/download/text").response(asSttpFile(file))

        Future(req.send()).flatMap(_.toFuture()).failed.collect {
          case caught: IOException =>
            caught.getMessage shouldBe s"File ${file.toFile.getAbsolutePath} exists - overwriting prohibited"
        }
      }
    }

    "not fail when file exists and overwrite flag is true" in {
      withTemporaryFile(Some(testBodyBytes)) { file =>
        val req = sttp
          .get(uri"$endpoint/download/text")
          .response(asSttpFile(file, overwrite = true))
        req.send().toFuture().flatMap { resp =>
          md5FileHash(resp.unsafeBody).map { _ shouldBe textFileHash }
        }
      }
    }
  }

  // scalajs only supports US_ASCII, ISO_8859_1, UTF_8, UTF_16BE, UTF_16LE, UTF_16
  "encoding" - {
    "read response body encoded using ISO-8859-2, as specified in the header, overriding the default" in {
      val request = sttp.get(uri"$endpoint/respond_with_iso_8859_2")

      request.send().toFuture().map { response =>
        response.unsafeBody should be(textWithSpecialCharacters)
      }
    }
  }
}
