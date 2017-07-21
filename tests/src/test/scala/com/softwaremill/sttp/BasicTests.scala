package com.softwaremill.sttp

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.{DateTime, FormData}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import com.softwaremill.sttp.akkahttp.AkkaHttpSttpHandler
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import better.files._
import com.softwaremill.sttp.asynchttpclient.future.FutureAsyncHttpClientHandler
import com.softwaremill.sttp.asynchttpclient.monix.MonixAsyncHttpClientHandler
import com.softwaremill.sttp.asynchttpclient.scalaz.ScalazAsyncHttpClientHandler

import scala.language.higherKinds

class BasicTests
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with StrictLogging
    with IntegrationPatience
    with TestHttpServer
    with ForceWrapped {

  private def paramsToString(m: Map[String, String]): String =
    m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

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
          complete(
            List("GET", "/echo", paramsToString(params))
              .filter(_.nonEmpty)
              .mkString(" "))
        }
      } ~
        post {
          parameterMap { params =>
            entity(as[String]) { body: String =>
              complete(
                List("POST", "/echo", paramsToString(params), body)
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
        setCookie(
          HttpCookie("c",
                     "v",
                     expires = Some(DateTime(1997, 12, 8, 12, 49, 12)))) {
          complete("ok")
        }
      } ~ get {
        setCookie(
          HttpCookie("cookie1",
                     "value1",
                     secure = true,
                     httpOnly = true,
                     maxAge = Some(123L))) {
          setCookie(HttpCookie("cookie2", "value2")) {
            setCookie(
              HttpCookie("cookie3",
                         "",
                         domain = Some("xyz"),
                         path = Some("a/b/c"))) {
              complete("ok")
            }
          }
        }
      }
    } ~ path("secure_basic") {
      authenticateBasic("test realm", {
        case c @ Credentials.Provided(un)
            if un == "adam" && c.verify("1234") =>
          Some(un)
        case _ => None
      }) { userName =>
        complete(s"Hello, $userName!")
      }
    } ~ path("compress") {
      encodeResponseWith(Gzip, Deflate, NoCoding) {
        complete("I'm compressed!")
      }
    }

  override def port = 51823

  runTests("HttpURLConnection")(HttpURLConnectionSttpHandler,
                                ForceWrappedValue.id)
  runTests("Akka HTTP")(new AkkaHttpSttpHandler(actorSystem),
                        ForceWrappedValue.future)
  runTests("Async Http Client - Future")(new FutureAsyncHttpClientHandler(),
                                         ForceWrappedValue.future)
  runTests("Async Http Client - Scalaz")(new ScalazAsyncHttpClientHandler(),
                                         ForceWrappedValue.scalazTask)
  runTests("Async Http Client - Monix")(new MonixAsyncHttpClientHandler(),
                                        ForceWrappedValue.monixTask)

  def runTests[R[_]](name: String)(
      implicit handler: SttpHandler[R, Nothing],
      forceResponse: ForceWrappedValue[R]): Unit = {

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

    def parseResponseTests(): Unit = {
      name should "parse response as string" in {
        val response = postEcho.body(testBody).send().force()
        response.body should be(expectedPostEchoResponse)
      }

      name should "parse response as string with mapping using map" in {
        val response = postEcho
          .body(testBody)
          .response(asString.map(_.length))
          .send()
          .force()
        response.body should be(expectedPostEchoResponse.length)
      }

      name should "parse response as string with mapping using mapResponse" in {
        val response = postEcho
          .body(testBody)
          .mapResponse(_.length)
          .send()
          .force()
        response.body should be(expectedPostEchoResponse.length)
      }

      name should "parse response as a byte array" in {
        val response =
          postEcho.body(testBody).response(asByteArray).send().force()
        val fc = new String(response.body, "UTF-8")
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
        response.body.toList should be(params)
      }
    }

    def parameterTests(): Unit = {
      name should "make a get request with parameters" in {
        val response = sttp
          .get(uri"$endpoint/echo?p2=v2&p1=v1")
          .send()
          .force()

        response.body should be("GET /echo p1=v1 p2=v2")
      }
    }

    def bodyTests(): Unit = {
      name should "post a string" in {
        val response = postEcho.body(testBody).send().force()
        response.body should be(expectedPostEchoResponse)
      }

      name should "post a byte array" in {
        val response =
          postEcho.body(testBodyBytes).send().force()
        response.body should be(expectedPostEchoResponse)
      }

      name should "post an input stream" in {
        val response = postEcho
          .body(new ByteArrayInputStream(testBodyBytes))
          .send()
          .force()
        response.body should be(expectedPostEchoResponse)
      }

      name should "post a byte buffer" in {
        val response = postEcho
          .body(ByteBuffer.wrap(testBodyBytes))
          .send()
          .force()
        response.body should be(expectedPostEchoResponse)
      }

      name should "post a file" in {
        val f = File.newTemporaryFile().write(testBody)
        try {
          val response = postEcho.body(f.toJava).send().force()
          response.body should be(expectedPostEchoResponse)
        } finally f.delete()
      }

      name should "post a path" in {
        val f = File.newTemporaryFile().write(testBody)
        try {
          val response =
            postEcho.body(f.toJava.toPath).send().force()
          response.body should be(expectedPostEchoResponse)
        } finally f.delete()
      }

      name should "post form data" in {
        val response = sttp
          .post(uri"$endpoint/echo/form_params/as_string")
          .body("a" -> "b", "c" -> "d")
          .send()
          .force()
        response.body should be("a=b c=d")
      }

      name should "post form data with special characters" in {
        val response = sttp
          .post(uri"$endpoint/echo/form_params/as_string")
          .body("a=" -> "/b", "c:" -> "/d")
          .send()
          .force()
        response.body should be("a==/b c:=/d")
      }
    }

    def headerTests(): Unit = {
      val getHeaders = sttp.get(uri"$endpoint/set_headers")

      name should "read response headers" in {
        val response = getHeaders.response(sttpIgnore).send().force()
        response.headers should have length (6)
        response.headers("Cache-Control").toSet should be(
          Set("no-cache", "max-age=1000"))
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
            Cookie("cookie1",
                   "value1",
                   secure = true,
                   httpOnly = true,
                   maxAge = Some(123L)),
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
        resp.header("WWW-Authenticate") should be(
          Some("""Basic realm="test realm",charset=UTF-8"""))
      }

      name should "perform basic authorization" in {
        val req = secureBasic.auth.basic("adam", "1234")
        val resp = req.send().force()
        resp.code should be(200)
        resp.body should be("Hello, adam!")
      }
    }

    def compressionTests(): Unit = {
      val compress = sttp.get(uri"$endpoint/compress")
      val decompressedBody = "I'm compressed!"

      name should "decompress using the default accept encoding header" in {
        val req = compress
        val resp = req.send().force()
        resp.body should be(decompressedBody)
      }

      name should "decompress using gzip" in {
        val req =
          compress.header("Accept-Encoding", "gzip", replaceExisting = true)
        val resp = req.send().force()
        resp.body should be(decompressedBody)
      }

      name should "decompress using deflate" in {
        val req =
          compress.header("Accept-Encoding", "deflate", replaceExisting = true)
        val resp = req.send().force()
        resp.body should be(decompressedBody)
      }

      name should "work despite providing an unsupported encoding" in {
        val req =
          compress.header("Accept-Encoding", "br", replaceExisting = true)
        val resp = req.send().force()
        resp.body should be(decompressedBody)
      }
    }
  }
}
