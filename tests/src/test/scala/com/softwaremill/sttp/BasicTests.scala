package com.softwaremill.sttp

import java.io.{ByteArrayInputStream, IOException}
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.util.ByteString
import better.files._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import com.softwaremill.sttp.asynchttpclient.future.AsyncHttpClientFutureBackend
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.softwaremill.sttp.asynchttpclient.scalaz.AsyncHttpClientScalazBackend
import com.softwaremill.sttp.okhttp.monix.OkHttpMonixBackend
import com.softwaremill.sttp.okhttp.{OkHttpFutureBackend, OkHttpSyncBackend}
import com.softwaremill.sttp.testing.streaming.ConvertToFuture
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{path => _, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds

class BasicTests
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with OptionValues
    with StrictLogging
    with IntegrationPatience
    with TestHttpServer
    with ForceWrapped
    with BeforeAndAfterEach {

  override def afterEach() {
    val file = File(outPath)
    if (file.exists) file.delete()
  }

  private def paramsToString(m: Map[String, String]): String =
    m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  private val textFile =
    new java.io.File("tests/src/test/resources/textfile.txt")
  private val binaryFile =
    new java.io.File("tests/src/test/resources/binaryfile.jpg")
  private val outPath = Paths.get("out")
  private val textWithSpecialCharacters = "Żółć!"

  override val serverRoutes: Route =
    pathPrefix("echo") {
      pathPrefix("form_params") {
        formFieldMap { params =>
          path("as_string") {
            complete(paramsToString(params))
          } ~
            path("as_params") {
              complete(FormData(params))
            }
        }
      } ~ get {
        parameterMap { params =>
          complete(List("GET", "/echo", paramsToString(params))
            .filter(_.nonEmpty)
            .mkString(" "))
        }
      } ~
        post {
          parameterMap { params =>
            entity(as[String]) { body: String =>
              complete(List("POST", "/echo", paramsToString(params), body)
                .filter(_.nonEmpty)
                .mkString(" "))
            }
          }
        }
    } ~ path("set_headers") {
      get {
        respondWithHeader(`Cache-Control`(`max-age`(1000L))) {
          respondWithHeader(`Cache-Control`(`no-cache`)) {
            complete("ok")
          }
        }
      }
    } ~ pathPrefix("set_cookies") {
      path("with_expires") {
        setCookie(HttpCookie("c", "v", expires = Some(DateTime(1997, 12, 8, 12, 49, 12)))) {
          complete("ok")
        }
      } ~ get {
        setCookie(HttpCookie("cookie1", "value1", secure = true, httpOnly = true, maxAge = Some(123L))) {
          setCookie(HttpCookie("cookie2", "value2")) {
            setCookie(HttpCookie("cookie3", "", domain = Some("xyz"), path = Some("a/b/c"))) {
              complete("ok")
            }
          }
        }
      }
    } ~ path("secure_basic") {
      authenticateBasic("test realm", {
        case c @ Credentials.Provided(un) if un == "adam" && c.verify("1234") =>
          Some(un)
        case _ => None
      }) { userName =>
        complete(s"Hello, $userName!")
      }
    } ~ path("compress") {
      encodeResponseWith(Gzip, Deflate, NoCoding) {
        complete("I'm compressed!")
      }
    } ~ pathPrefix("download") {
      path("binary") {
        getFromFile(binaryFile)
      } ~ path("text") {
        getFromFile(textFile)
      }
    } ~ pathPrefix("multipart") {
      entity(as[akka.http.scaladsl.model.Multipart.FormData]) { fd =>
        complete {
          fd.parts
            .mapAsync(1) { p =>
              val fv = p.entity.dataBytes.runFold(ByteString())(_ ++ _)
              fv.map(_.utf8String)
                .map(v => p.name + "=" + v + p.filename.fold("")(fn => s" ($fn)"))
            }
            .runFold(Vector.empty[String])(_ :+ _)
            .map(v => v.mkString(", "))
        }
      }
    } ~ pathPrefix("redirect") {
      path("r1") {
        redirect("/redirect/r2", StatusCodes.TemporaryRedirect)
      } ~
        path("r2") {
          redirect("/redirect/r3", StatusCodes.PermanentRedirect)
        } ~
        path("r3") {
          redirect("/redirect/r4", StatusCodes.Found)
        } ~
        path("r4") {
          complete("819")
        } ~
        path("loop") {
          redirect("/redirect/loop", StatusCodes.Found)
        }
    } ~ pathPrefix("timeout") {
      complete {
        akka.pattern.after(1.second, using = actorSystem.scheduler)(Future.successful("Done"))
      }
    } ~ path("empty_unauthorized_response") {
      post {
        import akka.http.scaladsl.model._
        complete(
          HttpResponse(
            status = StatusCodes.Unauthorized,
            headers = Nil,
            entity = HttpEntity.Empty,
            protocol = HttpProtocols.`HTTP/1.1`
          ))
      }
    } ~ path("respond_with_iso_8859_2") {
      get { ctx =>
        val entity =
          HttpEntity(MediaTypes.`text/plain`.withCharset(HttpCharset.custom("ISO-8859-2")), textWithSpecialCharacters)
        ctx.complete(HttpResponse(200, entity = entity))
      }
    }

  override def port = 51823

  var closeBackends: List[() => Unit] = Nil

  runTests("HttpURLConnection")(HttpURLConnectionBackend(), ConvertToFuture.id)
  runTests("TryHttpURLConnection")(TryHttpURLConnectionBackend(), ConvertToFuture.scalaTry)
  runTests("Akka HTTP")(AkkaHttpBackend.usingActorSystem(actorSystem), ConvertToFuture.future)
  runTests("Async Http Client - Future")(AsyncHttpClientFutureBackend(), ConvertToFuture.future)
  runTests("Async Http Client - Scalaz")(AsyncHttpClientScalazBackend(),
                                         com.softwaremill.sttp.impl.scalaz.convertToFuture)
  runTests("Async Http Client - Monix")(AsyncHttpClientMonixBackend(), com.softwaremill.sttp.impl.monix.convertToFuture)
  runTests("Async Http Client - Cats Effect")(AsyncHttpClientCatsBackend[cats.effect.IO](),
                                              com.softwaremill.sttp.impl.cats.convertToFuture)
  runTests("OkHttpSyncClientHandler")(OkHttpSyncBackend(), ConvertToFuture.id)
  runTests("OkHttpAsyncClientHandler - Future")(OkHttpFutureBackend(), ConvertToFuture.future)
  runTests("OkHttpAsyncClientHandler - Monix")(OkHttpMonixBackend(), com.softwaremill.sttp.impl.monix.convertToFuture)

  def runTests[R[_]](name: String)(implicit
                                   backend: SttpBackend[R, Nothing],
                                   convertToFuture: ConvertToFuture[R]): Unit = {

    closeBackends = (() => backend.close()) :: closeBackends

    val postEcho = sttp.post(uri"$endpoint/echo")
    val testBody = "this is the body"
    val testBodyBytes = testBody.getBytes("UTF-8")
    val expectedPostEchoResponse = "POST /echo this is the body"

    val sttpIgnore = com.softwaremill.sttp.ignore

    parseResponseTests()
    parameterTests()
    bodyTests()
    headerTests()
    errorsTests()
    cookiesTests()
    authTests()
    compressionTests()
    downloadFileTests()
    multipartTests()
    redirectTests()
    timeoutTests()
    emptyResponseTests()
    encodingTests()

    def parseResponseTests(): Unit = {
      name should "parse response as string" in {
        val response = postEcho.body(testBody).send().force()
        response.unsafeBody should be(expectedPostEchoResponse)
      }

      name should "parse response as string with mapping using map" in {
        val response = postEcho
          .body(testBody)
          .response(asString.map(_.length))
          .send()
          .force()
        response.unsafeBody should be(expectedPostEchoResponse.length)
      }

      name should "parse response as string with mapping using mapResponse" in {
        val response = postEcho
          .body(testBody)
          .mapResponse(_.length)
          .send()
          .force()
        response.unsafeBody should be(expectedPostEchoResponse.length)
      }

      name should "parse response as a byte array" in {
        val response =
          postEcho.body(testBody).response(asByteArray).send().force()
        val fc = new String(response.unsafeBody, "UTF-8")
        fc should be(expectedPostEchoResponse)
      }

      name should "parse response as parameters" in {
        val params = List("a" -> "b", "c" -> "d", "e=" -> "&f")
        val response = sttp
          .post(uri"$endpoint/echo/form_params/as_params")
          .body(params: _*)
          .response(asParams)
          .send()
          .force()
        response.unsafeBody.toList should be(params)
      }
    }

    def parameterTests(): Unit = {
      name should "make a get request with parameters" in {
        val response = sttp
          .get(uri"$endpoint/echo?p2=v2&p1=v1")
          .send()
          .force()

        response.unsafeBody should be("GET /echo p1=v1 p2=v2")
      }
    }

    def bodyTests(): Unit = {
      name should "post a string" in {
        val response = postEcho.body(testBody).send().force()
        response.unsafeBody should be(expectedPostEchoResponse)
      }

      name should "post a byte array" in {
        val response =
          postEcho.body(testBodyBytes).send().force()
        response.unsafeBody should be(expectedPostEchoResponse)
      }

      name should "post an input stream" in {
        val response = postEcho
          .body(new ByteArrayInputStream(testBodyBytes))
          .send()
          .force()
        response.unsafeBody should be(expectedPostEchoResponse)
      }

      name should "post a byte buffer" in {
        val response = postEcho
          .body(ByteBuffer.wrap(testBodyBytes))
          .send()
          .force()
        response.unsafeBody should be(expectedPostEchoResponse)
      }

      name should "post a file" in {
        val f = File.newTemporaryFile().write(testBody)
        try {
          val response = postEcho.body(f.toJava).send().force()
          response.unsafeBody should be(expectedPostEchoResponse)
        } finally f.delete()
      }

      name should "post a path" in {
        val f = File.newTemporaryFile().write(testBody)
        try {
          val response =
            postEcho.body(f.toJava.toPath).send().force()
          response.unsafeBody should be(expectedPostEchoResponse)
        } finally f.delete()
      }

      name should "post form data" in {
        val response = sttp
          .post(uri"$endpoint/echo/form_params/as_string")
          .body("a" -> "b", "c" -> "d")
          .send()
          .force()
        response.unsafeBody should be("a=b c=d")
      }

      name should "post form data with special characters" in {
        val response = sttp
          .post(uri"$endpoint/echo/form_params/as_string")
          .body("a=" -> "/b", "c:" -> "/d")
          .send()
          .force()
        response.unsafeBody should be("a==/b c:=/d")
      }

      name should "post without a body" in {
        val response = postEcho.send().force()
        response.unsafeBody should be("POST /echo")
      }
    }

    def headerTests(): Unit = {
      val getHeaders = sttp.get(uri"$endpoint/set_headers")

      name should "read response headers" in {
        val response = getHeaders.response(sttpIgnore).send().force()
        response.headers should have length (6)
        response.headers("Cache-Control").toSet should be(Set("no-cache", "max-age=1000"))
        response.header("Server") should be('defined)
        response.header("server") should be('defined)
        response.header("Server").get should startWith("akka-http")
        response.contentType should be(Some("text/plain; charset=UTF-8"))
        response.contentLength should be(Some(2L))
      }
    }

    def errorsTests(): Unit = {
      val getHeaders = sttp.post(uri"$endpoint/set_headers")

      name should "return 405 when method not allowed" in {
        val response = getHeaders.response(sttpIgnore).send().force()
        response.code should be(405)
        response.isClientError should be(true)
        response.body should be('left)
      }
    }

    def cookiesTests(): Unit = {
      name should "read response cookies" in {
        val response =
          sttp
            .get(uri"$endpoint/set_cookies")
            .response(sttpIgnore)
            .send()
            .force()
        response.cookies should have length (3)
        response.cookies.toSet should be(
          Set(
            Cookie("cookie1", "value1", secure = true, httpOnly = true, maxAge = Some(123L)),
            Cookie("cookie2", "value2"),
            Cookie("cookie3", "", domain = Some("xyz"), path = Some("a/b/c"))
          ))
      }

      name should "read response cookies with the expires attribute" in {
        val response = sttp
          .get(uri"$endpoint/set_cookies/with_expires")
          .response(sttpIgnore)
          .send()
          .force()
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

    def authTests(): Unit = {
      val secureBasic = sttp.get(uri"$endpoint/secure_basic")

      name should "return a 401 when authorization fails" in {
        val req = secureBasic
        val resp = req.send().force()
        resp.code should be(401)
        resp.header("WWW-Authenticate") should be(Some("""Basic realm="test realm",charset=UTF-8"""))
      }

      name should "perform basic authorization" in {
        val req = secureBasic.auth.basic("adam", "1234")
        val resp = req.send().force()
        resp.code should be(200)
        resp.unsafeBody should be("Hello, adam!")
      }
    }

    def compressionTests(): Unit = {
      val compress = sttp.get(uri"$endpoint/compress")
      val decompressedBody = "I'm compressed!"

      name should "decompress using the default accept encoding header" in {
        val req = compress
        val resp = req.send().force()
        resp.unsafeBody should be(decompressedBody)
      }

      name should "decompress using gzip" in {
        val req =
          compress.header("Accept-Encoding", "gzip", replaceExisting = true)
        val resp = req.send().force()
        resp.unsafeBody should be(decompressedBody)
      }

      name should "decompress using deflate" in {
        val req =
          compress.header("Accept-Encoding", "deflate", replaceExisting = true)
        val resp = req.send().force()
        resp.unsafeBody should be(decompressedBody)
      }

      name should "work despite providing an unsupported encoding" in {
        val req =
          compress.header("Accept-Encoding", "br", replaceExisting = true)
        val resp = req.send().force()
        resp.unsafeBody should be(decompressedBody)
      }
    }

    def downloadFileTests(): Unit = {
      import CustomMatchers._

      name should "download a binary file using asFile" in {
        val file = outPath.resolve("binaryfile.jpg").toFile
        val req =
          sttp.get(uri"$endpoint/download/binary").response(asFile(file))
        val resp = req.send().force()

        resp.unsafeBody shouldBe file
        file should exist
        file should haveSameContentAs(binaryFile)
      }

      name should "download a text file using asFile" in {
        val file = outPath.resolve("textfile.txt").toFile
        val req =
          sttp.get(uri"$endpoint/download/text").response(asFile(file))
        val resp = req.send().force()

        resp.unsafeBody shouldBe file
        file should exist
        file should haveSameContentAs(textFile)
      }

      name should "download a binary file using asPath" in {
        val path = outPath.resolve("binaryfile.jpg")
        val req =
          sttp.get(uri"$endpoint/download/binary").response(asPath(path))
        val resp = req.send().force()

        resp.unsafeBody shouldBe path
        path.toFile should exist
        path.toFile should haveSameContentAs(binaryFile)
      }

      name should "download a text file using asPath" in {
        val path = outPath.resolve("textfile.txt")
        val req =
          sttp.get(uri"$endpoint/download/text").response(asPath(path))
        val resp = req.send().force()

        resp.unsafeBody shouldBe path
        path.toFile should exist
        path.toFile should haveSameContentAs(textFile)
      }

      name should "fail at trying to save file to a restricted location" in {
        val path = Paths.get("/").resolve("textfile.txt")
        val req =
          sttp.get(uri"$endpoint/download/text").response(asPath(path))
        val caught = intercept[IOException] {
          req.send().force()
        }

        caught.getMessage shouldBe "Permission denied"
      }

      name should "fail when file exists and overwrite flag is false" in {
        val path = outPath.resolve("textfile.txt")
        path.toFile.getParentFile.mkdirs()
        path.toFile.createNewFile()
        val req =
          sttp.get(uri"$endpoint/download/text").response(asPath(path))

        val caught = intercept[IOException] {
          req.send().force()
        }

        caught.getMessage shouldBe s"File ${path.toFile.getAbsolutePath} exists - overwriting prohibited"

      }

      name should "not fail when file exists and overwrite flag is true" in {
        val path = outPath.resolve("textfile.txt")
        path.toFile.getParentFile.mkdirs()
        path.toFile.createNewFile()
        val req =
          sttp
            .get(uri"$endpoint/download/text")
            .response(asPath(path, overwrite = true))
        val resp = req.send().force()

        resp.unsafeBody shouldBe path
        path.toFile should exist
        path.toFile should haveSameContentAs(textFile)
      }
    }

    def multipartTests(): Unit = {
      val mp = sttp.post(uri"$endpoint/multipart")

      name should "send a multipart message" in {
        val req = mp.multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
        val resp = req.send().force()
        resp.unsafeBody should be("p1=v1, p2=v2")
      }

      name should "send a multipart message with filenames" in {
        val req = mp.multipartBody(multipart("p1", "v1").fileName("f1"), multipart("p2", "v2").fileName("f2"))
        val resp = req.send().force()
        resp.unsafeBody should be("p1=v1 (f1), p2=v2 (f2)")
      }

      name should "send a multipart message with a file" in {
        val f = File.newTemporaryFile().write(testBody)
        try {
          val req =
            mp.multipartBody(multipart("p1", f.toJava), multipart("p2", "v2"))
          val resp = req.send().force()
          resp.unsafeBody should be(s"p1=$testBody (${f.name}), p2=v2")
        } finally f.delete()
      }
    }

    def redirectTests(): Unit = {
      val r1 = sttp.post(uri"$endpoint/redirect/r1")
      val r2 = sttp.post(uri"$endpoint/redirect/r2")
      val r3 = sttp.post(uri"$endpoint/redirect/r3")
      val r4response = "819"
      val loop = sttp.post(uri"$endpoint/redirect/loop")

      name should "not redirect when redirects shouldn't be followed (temporary)" in {
        val resp = r1.followRedirects(false).send().force()
        resp.code should be(307)
        resp.body should be('left)
        resp.history should be('empty)
      }

      name should "not redirect when redirects shouldn't be followed (permanent)" in {
        val resp = r2.followRedirects(false).send().force()
        resp.code should be(308)
        resp.body should be('left)
      }

      name should "redirect when redirects should be followed" in {
        val resp = r2.send().force()
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
      }

      name should "redirect twice when redirects should be followed" in {
        val resp = r1.send().force()
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
      }

      name should "redirect when redirects should be followed, and the response is parsed" in {
        val resp = r2.response(asString.map(_.toInt)).send().force()
        resp.code should be(200)
        resp.unsafeBody should be(r4response.toInt)
      }

      name should "keep a single history entry of redirect responses" in {
        val resp = r3.send().force()
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
        resp.history should have size (1)
        resp.history(0).code should be(302)
      }

      name should "keep whole history of redirect responses" in {
        val resp = r1.send().force()
        resp.code should be(200)
        resp.unsafeBody should be(r4response)
        resp.history should have size (3)
        resp.history(0).code should be(307)
        resp.history(1).code should be(308)
        resp.history(2).code should be(302)
      }

      name should "break redirect loops" in {
        val resp = loop.send().force()
        resp.code should be(0)
        resp.history should have size (FollowRedirectsBackend.MaxRedirects)
      }

      name should "break redirect loops after user-specified count" in {
        val maxRedirects = 10
        val resp = loop.maxRedirects(maxRedirects).send().force()
        resp.code should be(0)
        resp.history should have size (maxRedirects)
      }

      name should "not redirect when maxRedirects is less than or equal to 0" in {
        val resp = loop.maxRedirects(-1).send().force()
        resp.code should be(302)
        resp.body should be('left)
        resp.history should be('empty)
      }
    }

    def timeoutTests(): Unit = {
      name should "fail if read timeout is not big enough" in {
        val request = sttp
          .get(uri"$endpoint/timeout")
          .readTimeout(200.milliseconds)
          .response(asString)

        intercept[Throwable] {
          request.send().force()
        }
      }

      name should "not fail if read timeout is big enough" in {
        val request = sttp
          .get(uri"$endpoint/timeout")
          .readTimeout(5.seconds)
          .response(asString)

        request.send().force().unsafeBody should be("Done")
      }
    }

    def emptyResponseTests(): Unit = {
      val postEmptyResponse = sttp
        .post(uri"$endpoint/empty_unauthorized_response")
        .body("{}")
        .contentType("application/json")

      name should "parse an empty error response as empty string" in {
        val response = postEmptyResponse.send().force()
        response.body should be(Left(""))
      }
    }

    def encodingTests(): Unit = {
      name should "read response body encoded using ISO-8859-2, as specified in the header, overriding the default" in {
        val request = sttp.get(uri"$endpoint/respond_with_iso_8859_2")

        request.send().force().unsafeBody should be(textWithSpecialCharacters)
      }
    }
  }

  override protected def afterAll(): Unit = {
    closeBackends.foreach(_())
    super.afterAll()
  }
}
