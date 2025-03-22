// {cat=JSON; effects=cats-effect; backend=HttpClient}: Receive & parse JSON using circe

//> using dep com.softwaremill.sttp.client4::circe:4.0.0-RC1
//> using dep com.softwaremill.sttp.client4::cats:4.0.0-RC1
//> using dep org.typelevel::cats-effect:3.5.7
//> using dep io.circe::circe-generic:0.14.12

package sttp.client4.examples.json

import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import cats.effect.IOApp
import cats.effect.IO
import cats.effect.ExitCode

object GetAndParseJsonZioCirce extends IOApp:
  case class HttpBinResponse(origin: String, headers: Map[String, String])

  override def run(args: List[String]): IO[ExitCode] = HttpClientCatsBackend
    .resource[IO]()
    .use: backend =>
      val request = basicRequest
        .get(uri"https://httpbin.org/get")
        .response(asJson[HttpBinResponse])

      for
        response <- request.send(backend)
        _ <- IO.println(s"Got response code: ${response.code}")
        _ <- IO.println(response.body.toString)
      yield ()
    .as(ExitCode.Success)
