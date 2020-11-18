package sttp.client3.testing.server

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._

import scala.concurrent.ExecutionContext.Implicits.global

object Http4sServer extends IOApp {

  private def formToString(m: UrlForm): String =
    m.values.view.mapValues(_.foldLeft("")(_ + _)).toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  private def paramsToString(m: Map[String, String]): String =
    m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  val echo: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
      val encoded = request.headers.iterator.map(h => h.name + "->" + h.value).mkString(",")
      Ok(encoded)

    case request @ GET -> Root / "echo" =>
      val response = List("GET", "/echo", paramsToString(request.params))
        .filter(_.nonEmpty)
        .mkString(" ")
      Ok(response)

    case request @ POST -> Root / "echo" =>
      val response = List("POST", "/echo", paramsToString(request.params))
        .filter(_.nonEmpty)
        .mkString(" ")
      Ok(response)

    case request @ POST -> Root / "echo" / "custom_status" / IntVar(status) =>
      request.decode[String] { body =>
        val response = List("POST", s"/echo/custom_status/$status", body)
          .filter(_.nonEmpty)
          .mkString(" ")

        IO(Response(Status(status)).withEntity(response))
      }
  }

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO](global)
      .bindHttp(8080, "localhost")
      .withHttpApp(echo.orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

}
