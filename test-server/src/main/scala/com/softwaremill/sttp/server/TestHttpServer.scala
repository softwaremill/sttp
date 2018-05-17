package com.softwaremill.sttp.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object TestHttpServer {

  def main(args: Array[String]): Unit = {
    val port = args.headOption.map(_.toInt).getOrElse(51823)

    Await.result(new TestHttpServer(port).start(), 10.seconds)
  }
}

class TestHttpServer(port: Int) extends AutoCloseable {

  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit val actorSystem: ActorSystem = ActorSystem("sttp-test-server")
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private def paramsToString(m: Map[String, String]): String =
    m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  private val textFile = new java.io.File("src/main/resources/textfile.txt")
  private val binaryFile = new java.io.File("src/main/resources/binaryfile.jpg")
  private val textWithSpecialCharacters = "Żółć!"

  val serverRoutes: Route =
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
    } ~ pathPrefix("streaming") {
      path("echo") {
        post {
          parameterMap { _ =>
            entity(as[String]) { body: String =>
              complete(body)
            }
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
        setCookie(
          HttpCookie(
            "cookie1",
            "value1",
            secure = true,
            httpOnly = true,
            maxAge = Some(123L)
          )
        ) {
          setCookie(HttpCookie("cookie2", "value2")) {
            setCookie(
              HttpCookie(
                "cookie3",
                "",
                domain = Some("xyz"),
                path = Some("a/b/c")
              )
            ) {
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
        akka.pattern.after(1.second, using = actorSystem.scheduler)(
          Future.successful("Done")
        )
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

  def start(): Future[Http.ServerBinding] = {
    Http().bindAndHandle(serverRoutes, "localhost", port)
  }

  def close(): Unit = {
    Await.result(
      actorSystem.terminate(),
      10.seconds
    )
  }
}
