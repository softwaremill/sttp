package sttp.client4.testing.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Coders._
import akka.http.scaladsl.coding.DeflateNoWrap
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.{RejectionHandler, Route}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.control.NoStackTrace

object HttpServer {
  def main(args: Array[String]): Unit = {
    val port = args.headOption.map(_.toInt).getOrElse(51823)

    Await.result(new HttpServer(port, println(_)).start(), 10.seconds)
  }
}

private class HttpServer(port: Int, info: String => Unit) extends AutoCloseable with CorsDirectives {
  import scala.concurrent.ExecutionContext.Implicits.global

  private var server: Option[Future[Http.ServerBinding]] = None

  private implicit val actorSystem: ActorSystem = ActorSystem("sttp-test-server")

  private val corsSettings = CorsSettings.defaultSettings
    .withExposedHeaders(List("Server", "Date", "Cache-Control", "Content-Length", "Content-Type", "WWW-Authenticate"))

  private def paramsToString(m: Map[String, String]): String =
    m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  private val textFile = toByteArray(getClass.getResourceAsStream("/textfile.txt"))
  private val binaryFile = toByteArray(getClass.getResourceAsStream("/binaryfile.jpg"))
  private val compressedFile = toByteArray(getClass.getResourceAsStream("/r3.gz"))
  private val textWithSpecialCharacters = "Żółć!"
  private val retryTestCache = new ConcurrentHashMap[String, Int]()

  private val serverRoutes: Route =
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
      } ~ pathPrefix("headers") {
        extractRequest { req =>
          complete(req.headers.map(h => h.name() + "->" + h.value()).mkString(","))
        }
      } ~ get {
        parameterMap { (params: Map[String, String]) =>
          complete(
            List("GET", "/echo", paramsToString(params))
              .filter(_.nonEmpty)
              .mkString(" ")
          )
        }
      } ~
        pathPrefix("custom_status") {
          path(IntNumber) { status =>
            post {
              entity(as[String]) { (body: String) =>
                complete(
                  HttpResponse(
                    status,
                    entity = List("POST", s"/echo/custom_status/$status", body)
                      .filter(_.nonEmpty)
                      .mkString(" ")
                  )
                )
              }
            }
          }
        } ~ path("exact") {
          post {
            entity(as[Array[Byte]])((body: Array[Byte]) => complete(body))
          }
        } ~ post {
          parameterMap { params =>
            entity(as[String]) { (body: String) =>
              complete(
                List("POST", "/echo", paramsToString(params), body)
                  .filter(_.nonEmpty)
                  .mkString(" ")
              )
            }
          }
        } ~ put {
          parameterMap { params =>
            entity(as[String]) { (body: String) =>
              complete(
                List("PUT", "/echo", paramsToString(params), body)
                  .filter(_.nonEmpty)
                  .mkString(" ")
              )
            }
          }
        }
    } ~ pathPrefix("streaming") {
      path("echo") {
        post {
          parameterMap(_ => entity(as[String])((body: String) => complete(body)))
        }
      } ~
        path("is_chunked") {
          post {
            extractRequest { req =>
              val isChunked = req.entity.isChunked().toString
              discardEntity(complete(isChunked))
            }
          }
        }
    } ~ pathPrefix("sse") {
      path("echo3") {
        import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
        post {
          entity(as[String]) { body =>
            complete {
              Source
                .tick(0.millis, 500.millis, NotUsed)
                .zipWithIndex
                .map { case (_, index) =>
                  ServerSentEvent(
                    data = body,
                    eventType = Some("test-event"),
                    id = Some(s"${index.toInt + 1}"),
                    retry = Some(42000)
                  )
                }
                .keepAlive(1.second, () => ServerSentEvent.heartbeat)
                .take(3)
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
    } ~ pathPrefix("cookies") {
      path("set_with_expires") {
        setCookie(HttpCookie("c", "v", expires = Some(DateTime(1997, 12, 8, 12, 49, 12)))) {
          complete("ok")
        }
      } ~ path("get_cookie2") {
        optionalCookie("cookie2") {
          case Some(c) => complete(s"${c.name}=${c.value}")
          case None    => complete("no cookie")
        }
      } ~ path("set") {
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
    } ~ path("set_content_type_header_with_encoding_in_quotes") {
      entity(as[String]) { (body: String) =>
        complete(
          HttpResponse(
            entity = HttpEntity(body).withContentType(
              ContentType(MediaType.custom("text/plain", binary = false), () => HttpCharset.custom("\"utf-8\""))
            )
          )
        )
      }
    } ~ path("secure_basic") {
      authenticateBasic(
        "test realm",
        {
          case c @ Credentials.Provided(un) if un == "adam" && c.verify("1234") =>
            Some(un)
          case _ => None
        }
      )(userName => complete(s"Hello, $userName!"))
    } ~ path("secure_digest") {
      get {
        import akka.http.scaladsl.model._
        extractCredentials {
          case Some(_) =>
            headerValueByName("Authorization") { authHeader =>
              if (
                authHeader.contains(
                  """Digest algorithm=MD5,
                      |cnonce=e5d93287aa8532c1f5df9e052fda4c38,
                      |nc=00000001,
                      |nonce="a2FzcGVya2FzcGVyCg==",
                      |qop=auth,
                      |realm=my-custom-realm,
                      |response=f1f784de97f8badb4acec7c5f85eb877,
                      |uri="/secure_digest",
                      |username=adam""".stripMargin.replaceAll("\n", "")
                )
              ) {
                complete(
                  HttpResponse(
                    status = StatusCodes.OK,
                    headers = Nil,
                    entity = HttpEntity.Empty,
                    protocol = HttpProtocols.`HTTP/1.1`
                  )
                )
              } else {
                complete(
                  HttpResponse(
                    status = StatusCodes.Unauthorized,
                    headers = Nil,
                    entity = HttpEntity.Empty,
                    protocol = HttpProtocols.`HTTP/1.1`
                  )
                )
              }
            }
          case None =>
            complete(
              HttpResponse(
                status = StatusCodes.Unauthorized,
                headers = List[HttpHeader](
                  `WWW-Authenticate`.apply(
                    HttpChallenge
                      .apply("Digest", "my-custom-realm", Map("qop" -> "auth", "nonce" -> "a2FzcGVya2FzcGVyCg=="))
                  )
                ),
                entity = HttpEntity.Empty,
                protocol = HttpProtocols.`HTTP/1.1`
              )
            )
        }
      }
    } ~ path("compress-unsupported") {
      respondWithHeader(`Content-Encoding`(HttpEncoding("secret-encoding"))) {
        complete("I'm compressed!")
      }
    } ~ path("compress-custom") {
      respondWithHeader(`Content-Encoding`(HttpEncoding("custom"))) {
        complete("I'm compressed, but who cares! Must be overwritten by client encoder")
      }
    } ~ path("compress") {
      encodeResponseWith(NoCoding, Gzip, Deflate) {
        complete("I'm compressed!")
      }
    } ~ path("compress-deflate-nowrap") {
      encodeResponseWith(DeflateNoWrap) {
        complete("I'm compressed!")
      }
    } ~ path("compress-empty-gzip") {
      encodeResponseWith(Gzip) {
        respondWithHeader(RawHeader("Content-Encoding", "gzip")) {
          complete(204, HttpEntity.Empty)
        }
      }
    } ~ path("compress-empty-deflate") {
      encodeResponseWith(Deflate) {
        respondWithHeader(RawHeader("Content-Encoding", "deflate")) {
          complete(204, HttpEntity.Empty)
        }
      }
    } ~ path("raw-gzip-file") {
      encodeResponseWith(Gzip) {
        respondWithHeader(RawHeader("Content-Encoding", "gzip")) {
          complete(HttpEntity(compressedFile))
        }
      }
    } ~ pathPrefix("download") {
      path("binary") {
        complete(HttpEntity(binaryFile))
      } ~ path("text") {
        complete(HttpEntity(textFile))
      }
    } ~ pathPrefix("multipart") {
      pathPrefix("other") {
        extractRequest { request =>
          entity(as[akka.http.scaladsl.model.Multipart.General]) { (fd: akka.http.scaladsl.model.Multipart.General) =>
            complete {
              fd.parts
                .mapAsync(1) { p =>
                  val fv = p.entity.dataBytes.runFold(ByteString())(_ ++ _)
                  fv.map(_.utf8String)
                }
                .runFold(Vector.empty[String])(_ :+ _)
                .map(v => v.mkString(", "))
                .map(v => s"${request.entity.contentType},$v")
            }
          }
        }
      } ~
        pathPrefix("content_type") {
          extractRequest { request =>
            entity(as[akka.http.scaladsl.model.Multipart.FormData]) {
              (fd: akka.http.scaladsl.model.Multipart.FormData) =>
                complete {
                  fd.parts
                    .mapAsync(1) { p =>
                      val fv = p.entity.dataBytes.runFold(ByteString())(_ ++ _)
                      fv.map(_.utf8String)
                        .map(v =>
                          p.name + "=" + v + p.filename
                            .fold("")(fn => s" ($fn)") + s" content-type: ${p.getEntity().getContentType}"
                        )
                    }
                    .runFold(Vector.empty[String])(_ :+ _)
                    .map(v => v.mkString(", "))
                }
            }
          }
        } ~
        entity(as[akka.http.scaladsl.model.Multipart.FormData]) { (fd: akka.http.scaladsl.model.Multipart.FormData) =>
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
        discardEntity(redirect("/redirect/r2", StatusCodes.TemporaryRedirect))
      } ~
        path("r2") {
          discardEntity(redirect("/redirect/r3", StatusCodes.PermanentRedirect))
        } ~
        path("r3") {
          discardEntity(redirect("/redirect/r4", StatusCodes.Found))
        } ~
        path("r4") {
          complete("819")
        } ~
        path("loop") {
          discardEntity(redirect("/redirect/loop", StatusCodes.Found))
        } ~
        pathPrefix("get_after_post") {
          path("r301") {
            discardEntity(redirect("/redirect/get_after_post/result", StatusCodes.MovedPermanently))
          } ~ path("r302") {
            discardEntity(redirect("/redirect/get_after_post/result", StatusCodes.Found))
          } ~ path("r303") {
            discardEntity(redirect("/redirect/get_after_post/result", StatusCodes.SeeOther))
          } ~ path("r307") {
            discardEntity(redirect("/redirect/get_after_post/result", StatusCodes.TemporaryRedirect))
          } ~ path("r308") {
            discardEntity(redirect("/redirect/get_after_post/result", StatusCodes.PermanentRedirect))
          } ~ path("result") {
            get(complete(s"GET")) ~
              entity(as[String])((body: String) => post(complete(s"POST$body")))
          }
        } ~ pathPrefix("strip_sensitive_headers") {
          path("r1") {
            discardEntity(redirect("/redirect/strip_sensitive_headers/result", StatusCodes.PermanentRedirect))
          } ~ path("result") {
            extractRequest((req: HttpRequest) => complete(s"${req.headers.mkString(",")}"))
          }
        } ~ pathPrefix("relative") {
          discardEntity(redirect("r4", StatusCodes.PermanentRedirect))
        }
    } ~ pathPrefix("error") {
      complete(
        HttpResponse(
          status = StatusCodes.OK,
          headers = Nil,
          entity = HttpEntity(
            MediaTypes.`application/octet-stream`,
            Source
              .single(ByteString(1))
              .concat(Source.failed(new RuntimeException("expected error") with NoStackTrace)): Source[ByteString, Any]
          ),
          protocol = HttpProtocols.`HTTP/1.1`
        )
      )
    } ~ pathPrefix("retry") { // #1616: calling the endpoint with the same tag will give status codes 401, 401, 400, 200, ...
      parameter("tag") { tag =>
        val current = Option(retryTestCache.get(tag)).getOrElse(0)
        retryTestCache.put(tag, current + 1)

        current match {
          case 0 => complete(StatusCodes.Unauthorized, "")
          case 1 => complete(StatusCodes.Unauthorized, "")
          case 2 => complete(StatusCodes.BadRequest, "")
          case _ => complete(StatusCodes.OK, "")
        }
      }
    } ~ pathPrefix("timeout") {
      complete {
        akka.pattern.after(2.seconds, using = actorSystem.scheduler)(
          Future.successful("Done")
        )
      }
    } ~ path("empty_unauthorized_response") {
      (post | head) {
        import akka.http.scaladsl.model._
        complete(
          HttpResponse(
            status = StatusCodes.Unauthorized,
            headers = Nil,
            entity = HttpEntity.Empty,
            protocol = HttpProtocols.`HTTP/1.1`
          )
        )
      }
    } ~ path("respond_with_iso_8859_2") {
      get { ctx =>
        val entity =
          HttpEntity(MediaTypes.`text/plain`.withCharset(HttpCharset.custom("ISO-8859-2")), textWithSpecialCharacters)
        ctx.complete(HttpResponse(200, entity = entity))
      }
    } ~ pathPrefix("ws") {
      path("echo") {
        handleWebSocketMessages(Flow[Message].mapConcat {
          case tm: TextMessage =>
            TextMessage(Source.single("echo: ") ++ tm.textStream) :: Nil
          case bm: BinaryMessage => bm :: Nil
        })
      } ~
        path("send_and_wait") {
          // send two messages and wait until the socket is closed
          handleWebSocketMessages(
            Flow.fromSinkAndSourceCoupled(
              Sink.ignore,
              Source(List(TextMessage("test10"), TextMessage("test20"))) ++ Source.maybe
            )
          )
        } ~
        path("send_and_expect_echo") {
          extractRequest { _ => // to make sure the route is evaluated on each request
            val sourcePromise = Promise[Source[Message, NotUsed]]()
            // send two messages and expect a correct echo response
            handleWebSocketMessages(
              Flow.fromSinkAndSourceCoupled(
                Sink.fold(1) { case (counter, msg) =>
                  val expectedMsg = s"test$counter-echo"
                  msg match {
                    case TextMessage.Strict(`expectedMsg`) =>
                      if (counter == 3) sourcePromise.success(Source.empty)
                      if (counter > 3) throw new IllegalArgumentException("Got more messages than expected!")
                      counter + 1
                    case _ =>
                      throw new IllegalArgumentException(s"Wrong message, expected: $expectedMsg, but got: $msg")
                  }
                },
                Source(List(TextMessage("test1"), TextMessage("test2"), TextMessage("test3"))) ++ Source
                  .lazyFutureSource(() => sourcePromise.future)
              )
            )
          }
        } ~
        path("header") {
          respondWithHeader(HttpHeader.parse("Correlation-ID", "ABC-XYZ-123").asInstanceOf[ParsingResult.Ok].header) {
            handleWebSocketMessages(Flow[Message].mapConcat {
              case tm: TextMessage =>
                TextMessage(Source.single("echo: ") ++ tm.textStream) :: Nil
              case bm: BinaryMessage => bm :: Nil
            })
          }
        }
    } ~ path("empty_content_encoding") {
      get {
        respondWithHeader(RawHeader("Content-Encoding", "")) {
          complete("OK")
        }
      }
    }

  def discardEntity(inner: Route): Route =
    extractRequest { request =>
      onComplete(request.entity.discardBytes().future()) { _ =>
        inner
      }
    }

  val corsServerRoutes: Route =
    handleRejections(CorsDirectives.corsRejectionHandler) {
      cors(corsSettings) {
        handleRejections(RejectionHandler.default) {
          serverRoutes
        }
      }
    }

  def start(): Future[Http.ServerBinding] =
    unbindServer().flatMap { _ =>
      val server = Http().bindAndHandle(corsServerRoutes, "localhost", port)
      this.server = Some(server)
      server
    }

  def close(): Unit = {
    val unbind = unbindServer()
    unbind.onComplete(_ => actorSystem.terminate())
    Await.result(
      unbind,
      10.seconds
    )
  }

  private def unbindServer(): Future[Done] =
    server.map(_.flatMap(_.unbind())).getOrElse(Future.successful(Done))

  private def transfer(is: InputStream, os: OutputStream): Unit = {
    var read = 0
    val buf = new Array[Byte](1024)

    @tailrec
    def transfer(): Unit = {
      read = is.read(buf, 0, buf.length)
      if (read != -1) {
        os.write(buf, 0, read)
        transfer()
      }
    }

    transfer()
  }

  private def toByteArray(is: InputStream): Array[Byte] = {
    val os = new ByteArrayOutputStream
    transfer(is, os)
    os.toByteArray
  }
}
