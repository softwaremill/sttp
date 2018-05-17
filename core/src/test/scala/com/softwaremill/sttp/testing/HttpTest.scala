package com.softwaremill.sttp.testing

import java.io.{ByteArrayInputStream, IOException}
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.time.{ZoneId, ZonedDateTime}

import better.files._
import com.softwaremill.sttp._
import com.softwaremill.sttp.testing.CustomMatchers._
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{AsyncFreeSpec, BeforeAndAfterAll, BeforeAndAfterEach, Matchers, OptionValues}
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.higherKinds

trait HttpTest[R[_]]
    extends AsyncFreeSpec
    with Matchers
    with ToFutureWrapper
    with OptionValues
    with IntegrationPatience
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  private val endpoint = "localhost:51823"

  override def afterEach() {
    val file = File(outPath)
    if (file.exists) file.delete()
  }

  private val textFile =
    new java.io.File("test-server/src/main/resources/textfile.txt")
  private val binaryFile =
    new java.io.File("test-server/src/main/resources/binaryfile.jpg")
  private val outPath = File.newTemporaryDirectory().path
  private val textWithSpecialCharacters = "Żółć!"

  implicit val backend: SttpBackend[R, Nothing]
  implicit val convertToFuture: ConvertToFuture[R]

  private val postEcho = sttp.post(uri"$endpoint/echo")
  private val testBody = "this is the body"
  private val testBodyBytes = testBody.getBytes("UTF-8")
  private val expectedPostEchoResponse = "POST /echo this is the body"

  private val sttpIgnore = com.softwaremill.sttp.ignore

  "parse response" - {
    "as string" in {
      postEcho.body(testBody).send().toFuture().map { response =>
        response.unsafeBody should be(expectedPostEchoResponse)
      }
    }

    "as string with mapping using map" in {
      postEcho
        .body(testBody)
        .response(asString.map(_.length))
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be(expectedPostEchoResponse.length)
        }
    }

    "as string with mapping using mapResponse" in {
      postEcho
        .body(testBody)
        .mapResponse(_.length)
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be(expectedPostEchoResponse.length)
        }
    }

    "as a byte array" in {
      postEcho.body(testBody).response(asByteArray).send().toFuture().map { response =>
        val fc = new String(response.unsafeBody, "UTF-8")
        fc should be(expectedPostEchoResponse)
      }
    }

    "as parameters" in {
      val params = List("a" -> "b", "c" -> "d", "e=" -> "&f")
      sttp
        .post(uri"$endpoint/echo/form_params/as_params")
        .body(params: _*)
        .response(asParams)
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody.toList should be(params)
        }
    }
  }

  "parameters" - {
    "make a get request with parameters" in {
      sttp
        .get(uri"$endpoint/echo?p2=v2&p1=v1")
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be("GET /echo p1=v1 p2=v2")
        }
    }
  }

  "body" - {
    "post a string" in {
      postEcho.body(testBody).send().toFuture().map { response =>
        response.unsafeBody should be(expectedPostEchoResponse)
      }
    }

    "post a byte array" in {
      postEcho.body(testBodyBytes).send().toFuture().map { response =>
        response.unsafeBody should be(expectedPostEchoResponse)
      }
    }

    "post an input stream" in {
      postEcho
        .body(new ByteArrayInputStream(testBodyBytes))
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be(expectedPostEchoResponse)
        }
    }

    "post a byte buffer" in {
      postEcho
        .body(ByteBuffer.wrap(testBodyBytes))
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be(expectedPostEchoResponse)
        }
    }

    "post a file" in {
      val f = File.newTemporaryFile().write(testBody)
      val result = postEcho.body(f.toJava).send().toFuture().map { response =>
        response.unsafeBody should be(expectedPostEchoResponse)
      }
      result.onComplete(_ => f.delete())
      result
    }

    "post a path" in {
      val f = File.newTemporaryFile().write(testBody)
      val result = postEcho.body(f.toJava.toPath).send().toFuture().map { response =>
        response.unsafeBody should be(expectedPostEchoResponse)
      }
      result.onComplete(_ => f.delete())
      result
    }

    "post form data" in {
      sttp
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a" -> "b", "c" -> "d")
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be("a=b c=d")
        }
    }

    "post form data with special characters" in {
      sttp
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a=" -> "/b", "c:" -> "/d")
        .send()
        .toFuture()
        .map { response =>
          response.unsafeBody should be("a==/b c:=/d")
        }
    }

    "post without a body" in {
      postEcho.send().toFuture().map { response =>
        response.unsafeBody should be("POST /echo")
      }
    }
  }

  "headers" - {
    val getHeaders = sttp.get(uri"$endpoint/set_headers")

    "read response headers" in {
      getHeaders.response(sttpIgnore).send().toFuture().map { response =>
        response.headers should have length (6)
        response.headers("Cache-Control").toSet should be(Set("no-cache", "max-age=1000"))
        response.header("Server") should be('defined)
        response.header("server") should be('defined)
        response.header("Server").get should startWith("akka-http")
        response.contentType should be(Some("text/plain; charset=UTF-8"))
        response.contentLength should be(Some(2L))
      }
    }
  }

  "errors" - {
    val getHeaders = sttp.post(uri"$endpoint/set_headers")

    "return 405 when method not allowed" in {
      getHeaders.response(sttpIgnore).send().toFuture().map { response =>
        response.code should be(405)
        response.isClientError should be(true)
        response.body should be('left)
      }
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

  "auth" - {
    val secureBasic = sttp.get(uri"$endpoint/secure_basic")

    "return a 401 when authorization fails" in {
      val req = secureBasic
      req.send().toFuture().map { resp =>
        resp.code should be(401)
        resp.header("WWW-Authenticate") should be(Some("""Basic realm="test realm",charset=UTF-8"""))
      }
    }

    "perform basic authorization" in {
      val req = secureBasic.auth.basic("adam", "1234")
      req.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be("Hello, adam!")
      }
    }
  }

  "compression" - {
    val compress = sttp.get(uri"$endpoint/compress")
    val decompressedBody = "I'm compressed!"

    "decompress using the default accept encoding header" in {
      val req = compress
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(decompressedBody)
      }
    }

    "decompress using gzip" in {
      val req = compress.header("Accept-Encoding", "gzip", replaceExisting = true)
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(decompressedBody)
      }
    }

    "decompress using deflate" in {
      val req = compress.header("Accept-Encoding", "deflate", replaceExisting = true)
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(decompressedBody)
      }
    }

    "work despite providing an unsupported encoding" in {
      val req = compress.header("Accept-Encoding", "br", replaceExisting = true)
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(decompressedBody)
      }
    }
  }

  "download file" - {

    "download a binary file using asFile" in {
      val file = outPath.resolve("binaryfile.jpg").toFile
      val req = sttp.get(uri"$endpoint/download/binary").response(asFile(file))
      req.send().toFuture().map { resp =>
        resp.unsafeBody shouldBe file
        file should exist
        file should haveSameContentAs(binaryFile)
      }
    }

    "download a text file using asFile" in {
      val file = outPath.resolve("textfile.txt").toFile
      val req = sttp.get(uri"$endpoint/download/text").response(asFile(file))
      req.send().toFuture().map { resp =>
        resp.unsafeBody shouldBe file
        file should exist
        file should haveSameContentAs(textFile)
      }
    }

    "download a binary file using asPath" in {
      val path = outPath.resolve("binaryfile.jpg")
      val req = sttp.get(uri"$endpoint/download/binary").response(asPath(path))
      req.send().toFuture().map { resp =>
        resp.unsafeBody shouldBe path
        path.toFile should exist
        path.toFile should haveSameContentAs(binaryFile)
      }
    }

    "download a text file using asPath" in {
      val path = outPath.resolve("textfile.txt")
      val req = sttp.get(uri"$endpoint/download/text").response(asPath(path))
      req.send().toFuture().map { resp =>
        resp.unsafeBody shouldBe path
        path.toFile should exist
        path.toFile should haveSameContentAs(textFile)
      }
    }

    "fail at trying to save file to a restricted location" in {
      val path = Paths.get("/").resolve("textfile.txt")
      val req = sttp.get(uri"$endpoint/download/text").response(asPath(path))
      Future(req.send()).flatMap(_.toFuture()).failed.collect {
        case caught: IOException => caught.getMessage shouldBe "Permission denied"
      }
    }

    "fail when file exists and overwrite flag is false" in {
      val path = outPath.resolve("textfile.txt")
      path.toFile.getParentFile.mkdirs()
      path.toFile.createNewFile()
      val req = sttp.get(uri"$endpoint/download/text").response(asPath(path))

      Future(req.send()).flatMap(_.toFuture()).failed.collect {
        case caught: IOException =>
          caught.getMessage shouldBe s"File ${path.toFile.getAbsolutePath} exists - overwriting prohibited"
      }
    }

    "not fail when file exists and overwrite flag is true" in {
      val path = outPath.resolve("textfile.txt")
      path.toFile.getParentFile.mkdirs()
      path.toFile.createNewFile()
      val req = sttp
        .get(uri"$endpoint/download/text")
        .response(asPath(path, overwrite = true))
      req.send().toFuture().map { resp =>
        resp.unsafeBody shouldBe path
        path.toFile should exist
        path.toFile should haveSameContentAs(textFile)
      }
    }
  }

  "multipart" - {
    val mp = sttp.post(uri"$endpoint/multipart")

    "send a multipart message" in {
      val req = mp.multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be("p1=v1, p2=v2")
      }
    }

    "send a multipart message with filenames" in {
      val req = mp.multipartBody(multipart("p1", "v1").fileName("f1"), multipart("p2", "v2").fileName("f2"))
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be("p1=v1 (f1), p2=v2 (f2)")
      }
    }

    "send a multipart message with a file" in {
      val f = File.newTemporaryFile().write(testBody)
      val req = mp.multipartBody(multipart("p1", f.toJava), multipart("p2", "v2"))
      val result = req.send().toFuture().map { resp =>
        resp.unsafeBody should be(s"p1=$testBody (${f.name}), p2=v2")
      }
      result.onComplete(_ => f.delete())
      result
    }
  }

  "redirect" - {
    val r1 = sttp.post(uri"$endpoint/redirect/r1")
    val r2 = sttp.post(uri"$endpoint/redirect/r2")
    val r3 = sttp.post(uri"$endpoint/redirect/r3")
    val r4response = "819"
    val loop = sttp.post(uri"$endpoint/redirect/loop")

    "not redirect when redirects shouldn't be followed (temporary)" in {
      r1.followRedirects(false).send().toFuture().map { resp =>
        resp.code should be(307)
        resp.body should be('left)
        resp.history should be('empty)
      }
    }

    "not redirect when redirects shouldn't be followed (permanent)" in {
      r2.followRedirects(false).send().toFuture().map { resp =>
        resp.code should be(308)
        resp.body should be('left)
      }
    }

    "redirect when redirects should be followed" in {
      r2.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
      }
    }

    "redirect twice when redirects should be followed" in {
      r1.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
      }
    }

    "redirect when redirects should be followed, and the response is parsed" in {
      r2.response(asString.map(_.toInt)).send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response.toInt)
      }
    }

    "keep a single history entry of redirect responses" in {
      r3.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
        resp.history should have size (1)
        resp.history(0).code should be(302)
      }
    }

    "keep whole history of redirect responses" in {
      r1.send().toFuture().map { resp =>
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
        resp.history should have size (3)
        resp.history(0).code should be(307)
        resp.history(1).code should be(308)
        resp.history(2).code should be(302)
      }
    }

    "break redirect loops" in {
      loop.send().toFuture().map { resp =>
        resp.code should be(0)
        resp.history should have size (FollowRedirectsBackend.MaxRedirects)
      }
    }

    "break redirect loops after user-specified count" in {
      val maxRedirects = 10
      loop.maxRedirects(maxRedirects).send().toFuture().map { resp =>
        resp.code should be(0)
        resp.history should have size (maxRedirects)
      }
    }

    "not redirect when maxRedirects is less than or equal to 0" in {
      loop.maxRedirects(-1).send().toFuture().map { resp =>
        resp.code should be(302)
        resp.body should be('left)
        resp.history should be('empty)
      }
    }
  }

  "timeout" - {
    "fail if read timeout is not big enough" in {
      val request = sttp
        .get(uri"$endpoint/timeout")
        .readTimeout(200.milliseconds)
        .response(asString)

      Future(request.send()).flatMap(_.toFuture()).failed.map { _ =>
        succeed
      }
    }

    "not fail if read timeout is big enough" in {
      val request = sttp
        .get(uri"$endpoint/timeout")
        .readTimeout(5.seconds)
        .response(asString)

      request.send().toFuture().map { response =>
        response.unsafeBody should be("Done")
      }
    }
  }

  "empty response" - {
    val postEmptyResponse = sttp
      .post(uri"$endpoint/empty_unauthorized_response")
      .body("{}")
      .contentType("application/json")

    "parse an empty error response as empty string" in {
      postEmptyResponse.send().toFuture().map { response =>
        response.body should be(Left(""))
      }
    }
  }

  "encoding" - {
    "read response body encoded using ISO-8859-2, as specified in the header, overriding the default" in {
      val request = sttp.get(uri"$endpoint/respond_with_iso_8859_2")

      request.send().toFuture().map { response =>
        response.unsafeBody should be(textWithSpecialCharacters)
      }
    }
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }

}
