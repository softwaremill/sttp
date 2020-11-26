package sttp.client3.testing.server

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.util.concurrent.Executors

import akka.util.ByteString
import cats.effect._
import cats.implicits._
import org.http4s.CacheDirective._
import org.http4s.EntityEncoder._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.multipart.Multipart
import org.http4s.server.AuthMiddleware
import org.http4s.server.blaze._
import org.http4s.server.middleware.CORS.DefaultCORSConfig
import org.http4s.server.middleware._
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.util.CaseInsensitiveString
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import org.http4s.{Http, _}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object Http4sServer extends IOApp {

  private def formToString(m: UrlForm): String =
    m.values.view.mapValues(_.foldLeft("")(_ + _)).toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  private def paramsToString(m: Map[String, String]): String =
    m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  private val textFile = toByteArray(getClass.getResourceAsStream("/textfile.txt"))
  private val binaryFile = toByteArray(getClass.getResourceAsStream("/binaryfile.jpg"))
  private val textWithSpecialCharacters = "Żółć!"

  private def corsSettings(service: Http[IO, IO]) = CORS(
    service,
    DefaultCORSConfig.copy(exposedHeaders =
      Some(Set("Server", "Date", "Cache-Control", "Content-Length", "Content-Type", "WWW-Authenticate"))
    )
  )

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

  val echo: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ -> Root => Ok()

    case request @ _ -> Root / "echo" / "as_string" =>
      request.decode[UrlForm] { params =>
        Ok(formToString(params))
      }

    case request @ _ -> Root / "echo" / "as_params" =>
      request.decode[UrlForm] { params =>
        //todo encode as FormData
        Ok(formToString(params))
      }

    case request @ _ -> Root / "echo" / "headers" =>
      val encoded = request.headers.iterator.map(h => s"${h.name}->${h.value}").mkString(",")
      Ok(encoded)

    case request @ GET -> Root / "echo" =>
      val response = List("GET", "/echo", paramsToString(request.params))
        .filter(_.nonEmpty)
        .mkString(" ")
      Ok(response)

    case request @ POST -> Root / "echo" =>
      request.decode[String] { body =>
        val response = List("POST", "/echo", paramsToString(request.params), body)
          .filter(_.nonEmpty)
          .mkString(" ")
        Ok(response)
      }

    case request @ POST -> Root / "echo" / "custom_status" / IntVar(status) =>
      request.decode[String] { body =>
        val response = List("POST", s"/echo/custom_status/$status", body)
          .filter(_.nonEmpty)
          .mkString(" ")

        IO(Response(Status(status)).withEntity(response))
      }

    case request @ POST -> Root / "streaming" / "echo" =>
      request.decode[String] { body =>
        Ok(body)
      }
  }

  val headers: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "set_headers" =>
      val response = Response[IO](Status.Ok)
        .withEntity("ok")
        .withHeaders(`Cache-Control`(`max-age`(1000.seconds)), `Cache-Control`(`no-cache`()))
      IO(response)

    case request @ _ -> Root / "set_content_type_header_with_encoding_in_quotes" =>
      request.decode[String] { body =>
        Ok(body).map(_.withHeaders(Header("Content-Type", "text/plain; charset=\"UTF-8\"")))
      }
  }

  val cookies: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ -> Root / "cookies" / "set_with_expires" =>
      Ok("ok").map(
        _.addCookie(
          ResponseCookie(
            name = "c",
            content = "v",
            expires = Some(HttpDate.unsafeFromEpochSecond(881585352000L))
          )
        )
      )
    case request @ _ -> Root / "cookies" / "get_cookie2" =>
      request.cookies.find(_.name == "cookie2") match {
        case Some(c) => Ok(s"${c.name}=${c.content}")
        case None    => Ok("no cookie")
      }

    case _ -> Root / "cookies" / "set" =>
      Ok("ok").map(
        _.addCookie(
          ResponseCookie(
            name = "cookie1",
            content = "value1",
            secure = true,
            httpOnly = true,
            maxAge = Some(123L),
            sameSite = SameSite.None
          )
        )
          .addCookie(ResponseCookie(name = "cookie2", content = "value2", sameSite = SameSite.None))
          .addCookie(
            ResponseCookie(
              name = "cookie3",
              content = "",
              domain = Some("xyz"),
              path = Some("a/b/c"),
              sameSite = SameSite.None
            )
          )
      )
  }

  val authMiddleware: AuthMiddleware[IO, String] = BasicAuth(
    "test realm",
    {
      case BasicCredentials("adam", "1234") => IO(Some("adam"))
      case _                                => IO(None)
    }
  )

  val authedService: AuthedRoutes[String, IO] =
    AuthedRoutes.of[String, IO] { case GET -> Root / "secure_basic" as user =>
      Ok(s"Hello, $user!")
    }

  val authed: HttpRoutes[IO] = authMiddleware(authedService)

  val secureDigest: HttpRoutes[IO] = HttpRoutes.of[IO] { case request @ GET -> Root / "secure_digest" =>
    request.headers.get(CaseInsensitiveString("Authorization")) match {
      case Some(authHeader) =>
        val correctHeader = authHeader.value.contains(
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

        if (correctHeader) {
          Ok()
        } else {
          IO(Response(Status.Unauthorized))
        }
      case None =>
        IO(
          Response(Status.Unauthorized).withHeaders(
            `WWW-Authenticate`(
              Challenge("Digest", "my-custom-realm", Map("qop" -> "auth", "nonce" -> "a2FzcGVya2FzcGVyCg=="))
            )
          )
        )
    }
  }

  val compression: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ -> Root / "compress-unsupported" =>
      Ok("I'm compressed!").map(_.withHeaders(`Content-Encoding`(ContentCoding.unsafeFromString("secret-encoding"))))
    case _ -> Root / "compress-custom" =>
      Ok("I'm compressed, but who cares! Must be overwritten by client encoder").map(
        _.withHeaders(`Content-Encoding`(ContentCoding.unsafeFromString("custom")))
      )
    case _ -> Root / "compress" =>
      Ok("I'm compressed!").map(
        _.withHeaders(`Content-Encoding`(ContentCoding.gzip))
      )
  }

  val download: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ -> Root / "download" / "binary" =>
      Ok().map(_.withEntity(binaryFile))
    case _ -> Root / "download" / "text" =>
      Ok().map(_.withEntity(textFile))
  }

  val multipartRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ _ -> Root / "multipart" =>
      request.decode[Multipart[IO]] { parts: Multipart[IO] =>
        parts.parts
          .map { part =>
            part.body.compile.toList
              .map(ByteString(_: _*).utf8String)
              .map(v => part.name.getOrElse("") + "=" + v + part.filename.fold("")(fn => s" ($fn)"))
          }
          .toList
          .sequence
          .flatMap { parsed =>
            Ok(parsed.mkString(", "))
          }
      }

    case request @ _ -> Root / "multipart" / "other" =>
      request.decode[Multipart[IO]] { parts: Multipart[IO] =>
        parts.parts
          .map { part =>
            part.body.compile.toList
              .map(ByteString(_: _*).utf8String)
          }
          .toList
          .sequence
          .flatMap { parsed =>
            Ok(s"${parts.headers.get(`Content-Type`).getOrElse("")},${parsed.mkString(", ")}")
          }
      }
  }

  val redirect: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ -> Root / "redirect" / "r1" =>
      TemporaryRedirect().map(_.withHeaders(Location(uri"/redirect/r2")))

    case _ -> Root / "redirect" / "r2" =>
      PermanentRedirect().map(_.withHeaders(Location(uri"/redirect/r3")))

    case _ -> Root / "redirect" / "r3" =>
      Found().map(_.withHeaders(Location(uri"/redirect/r4")))

    case _ -> Root / "redirect" / "r4" =>
      Ok("819")

    case _ -> Root / "redirect" / "loop" =>
      Found().map(_.withHeaders(Location(uri"/redirect/loop")))

    case _ -> Root / "redirect" / "get_after_post" / "r301" =>
      MovedPermanently().map(_.withHeaders(Location(uri"/redirect/get_after_post/result")))

    case _ -> Root / "redirect" / "get_after_post" / "r302" =>
      Found().map(_.withHeaders(Location(uri"/redirect/get_after_post/result")))

    case _ -> Root / "redirect" / "get_after_post" / "r303" =>
      SeeOther().map(_.withHeaders(Location(uri"/redirect/get_after_post/result")))

    case _ -> Root / "redirect" / "get_after_post" / "r307" =>
      TemporaryRedirect("").map(_.withHeaders(Location(uri"/redirect/get_after_post/result")))

    case _ -> Root / "redirect" / "get_after_post" / "r308" =>
      PermanentRedirect("").map(_.withHeaders(Location(uri"/redirect/get_after_post/result")))

    case GET -> Root / "redirect" / "get_after_post" / "result" =>
      Ok("GET")

    case request @ POST -> Root / "redirect" / "get_after_post" / "result" =>
      request.decode[String] { body =>
        Ok(s"POST$body")
      }

  }

  val errors: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ -> Root / "error" =>
      IO(
        Response[IO]()
          .withBodyStream(fs2.Stream[IO, Byte]('1').append { new RuntimeException; fs2.Stream[IO, Byte]('2') })
          .withHeaders(Header("Content-Type", "application/octet-stream"))
      )
    case _ -> Root / "timeout" =>
      val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
      implicit val timer: Timer[IO] = IO.timer(ec)
      IO.sleep(2.second).flatMap(_ => Ok("Done"))

    case (POST | HEAD) -> Root / "empty_unauthorized_response" =>
      IO(Response(Status.Unauthorized))

  }

  val isoText: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "respond_with_iso_8859_2" =>
    Ok().map(
      _.withEntity(textWithSpecialCharacters.getBytes("ISO-8859-2"))
        .withHeaders(Header("Content-Type", "text/plain; charset=ISO-8859-2"))
    )
  }

  val webSockets: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ -> Root / "ws" / "echo" =>
      import fs2.concurrent.Queue
      Queue.unbounded[IO, WebSocketFrame].flatMap { queue =>
        WebSocketBuilder[IO].build(
          queue.dequeue.collect { case Text(text) =>
            Text(s"echo: ${text._1}")
          },
          queue.enqueue
        )
      }

    case _ -> Root / "ws" / "send_and_wait" =>
      val sendToClient: fs2.Stream[IO, WebSocketFrame] =
        fs2.Stream[IO, WebSocketFrame](Text("test10"), Text("test20")) ++ fs2.Stream.eval[IO, WebSocketFrame](IO.never)
      val ignore: fs2.Pipe[IO, WebSocketFrame, Unit] = s => s.map(_ => ())
      WebSocketBuilder[IO].build(sendToClient, ignore)

    case _ -> Root / "ws" / "send_and_expect_echo" =>
      val sendToClient = fs2.Stream[IO, WebSocketFrame](Text("test1"), Text("test2"), Text("test3")) ++ fs2.Stream
        .eval[IO, WebSocketFrame](IO.never)
      val receive: fs2.Pipe[IO, WebSocketFrame, Unit] = s =>
        s.zip(sendToClient).map { case (incoming, expected) =>
          if (incoming != expected) {
            throw new IllegalArgumentException(s"Wrong message, expected: $expected, but got: $incoming")
          } else {
            ()
          }
        }
      WebSocketBuilder[IO].build(sendToClient, receive)

  }

  def run(args: List[String]): IO[ExitCode] = {
    val port = args.headOption.map(_.toInt).getOrElse(51823)
    BlazeServerBuilder[IO](global)
      .bindHttp(port, "localhost")
      .withHttpApp(
        corsSettings(
          (echo <+>
            headers <+>
            cookies <+>
            secureDigest <+>
            compression <+>
            download <+>
            multipartRequest <+>
            redirect <+>
            errors <+>
            isoText <+>
            webSockets <+>
            authed).orNotFound
        )
      )
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
